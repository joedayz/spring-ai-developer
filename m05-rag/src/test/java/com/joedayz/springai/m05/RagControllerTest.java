package com.joedayz.springai.m05;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "spring.ai.vectorstore.pgvector.dimensions=256",
        "rag.similarity-threshold=0.0" })
@AutoConfigureMockMvc
@Testcontainers
class RagControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @TestConfiguration
    static class FakeEmbeddings {
        @Bean
        @Primary
        EmbeddingModel embeddingModel() {
            return new BagOfWordsEmbeddingModel();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean ChatModel chatModel;

    @BeforeEach
    void setUp() throws Exception {
        reset(chatModel);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("ok")))));
        mvc.perform(post("/ingest")).andExpect(status().isOk());
    }

    private String lastPrompt() {
        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, atLeastOnce()).call(captor.capture());
        return captor.getAllValues().getLast().getInstructions().toString();
    }

    @Test
    void ingestEsIdempotente() throws Exception {
        int before = jdbc.queryForObject("select count(*) from vector_store", Integer.class);
        mvc.perform(post("/ingest")).andExpect(status().isOk());
        int after = jdbc.queryForObject("select count(*) from vector_store", Integer.class);
        assertThat(after).isEqualTo(before).isPositive();
    }

    @Test
    void searchEncuentraElDocumentoMasParecido() throws Exception {
        mvc.perform(get("/search").param("q", "cuántos meses de garantía tienen los productos electrónicos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value(org.hamcrest.Matchers.containsString("garantia.md")));
    }

    @Test
    void askInyectaElContextoRecuperadoEnElPrompt() throws Exception {
        mvc.perform(get("/ask").param("q", "¿Cuánto cuesta el envío exprés?")).andExpect(status().isOk());

        assertThat(lastPrompt()).contains("25 soles");
    }

    @Test
    void askFiltraPorMetadato() throws Exception {
        mvc.perform(get("/ask").param("q", "plazo de reembolso de devoluciones").param("category", "envios"))
                .andExpect(status().isOk());

        // con category=envios nunca debe llegar al prompt el texto de devoluciones
        assertThat(lastPrompt()).doesNotContain("30 días calendario").doesNotContain("reembolso se realiza");
    }

    @Test
    void askModularUsaElContextoYReescribeLaConsulta() throws Exception {
        mvc.perform(get("/ask-modular").param("q", "garantía de electrónicos")).andExpect(status().isOk());

        // una llamada para reescribir la consulta y otra para responder
        verify(chatModel, org.mockito.Mockito.atLeast(2)).call(any(Prompt.class));
    }

    @Test
    void ingestaDePdfSubidoPorElUsuario() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var doc = new PDDocument()) {
            var page = new PDPage();
            doc.addPage(page);
            try (var cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Los cupones de descuento vencen a los 90 dias de emitidos");
                cs.endText();
            }
            doc.save(bytes);
        }
        var file = new MockMultipartFile("file", "cupones.pdf", "application/pdf", bytes.toByteArray());

        mvc.perform(multipart("/ingest/pdf").file(file).param("category", "cupones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunks").value(1));

        mvc.perform(get("/ask").param("q", "cuando vencen los cupones de descuento").param("category", "cupones"))
                .andExpect(status().isOk());
        assertThat(lastPrompt()).containsIgnoringWhitespaces("90dias");
    }
}
