package com.joedayz.springai.m04;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "spring.datasource.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
        "course.summary.max-turns=3",
        "course.summary.keep-turns=1" })
@AutoConfigureMockMvc
class ConversationControllerTest {

    @Autowired MockMvc mvc;
    @Autowired SessionService sessionService;
    @MockitoBean ChatModel chatModel;

    @BeforeEach
    void setUp() {
        reset(chatModel);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("ok")))));
    }

    private void send(String path, String text) throws Exception {
        mvc.perform(post(path).contentType(MediaType.TEXT_PLAIN).content(text)).andExpect(status().isOk());
    }

    private String lastPromptText() {
        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, atLeastOnce()).call(captor.capture());
        return captor.getValue().getInstructions().toString();
    }

    @Test
    void laSesionEnRamLlevaElHistorialAlSiguienteTurno() throws Exception {
        send("/memory/ram1", "Me llamo Ana");
        send("/memory/ram1", "¿Cómo me llamo?");

        assertThat(lastPromptText()).contains("Me llamo Ana");
    }

    @Test
    void sesionesDistintasNoComparten() throws Exception {
        send("/memory/a", "secreto-de-a");
        send("/memory/b", "hola");

        assertThat(lastPromptText()).doesNotContain("secreto-de-a");
    }

    @Test
    void laSesionEnRamNoUsaLaBaseDeDatos() throws Exception {
        send("/memory/ram2", "solo en memoria");

        assertThat(sessionService.findById("ram2")).isNull();
    }

    @Test
    void laSesionJdbcGuardaLosMensajesEnBaseDeDatos() throws Exception {
        send("/persistent/db1", "Hola desde JDBC");

        assertThat(sessionService.getMessages("db1")).hasSize(2); // usuario + asistente
    }

    @Test
    void elAdvisorPropioEnmascaraPiiAntesDeGuardarYEnviar() throws Exception {
        send("/safe/pii1", "Mi correo es ana@acme.com y mi tel 987 654 321");

        assertThat(lastPromptText()).contains("[EMAIL]").doesNotContain("ana@acme.com");
        assertThat(sessionService.getMessages("pii1").toString())
                .contains("[EMAIL]", "[TELEFONO]").doesNotContain("ana@acme.com");
    }

    @Test
    void rechazaFormUrlEncodedParaQueElPiiNoEscapeAlEnmascarado() throws Exception {
        mvc.perform(post("/safe/pii2").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("Mi+correo+es+ana%40acme.com"))
                .andExpect(status().isUnsupportedMediaType());
        assertThat(sessionService.findById("pii2")).isNull();
    }

    @Test
    void alSuperarElUmbralSeResumeLaVistaPeroElLogSeConserva() throws Exception {
        for (int i = 1; i <= 4; i++) {
            send("/safe/sum1", "mensaje número " + i);
        }

        List<SessionEvent> view = sessionService.getEvents("sum1",
                org.springframework.ai.session.EventFilter.builder().build());
        assertThat(view).anyMatch(SessionEvent::isSynthetic);         // hay un resumen
        assertThat(sessionService.getActiveMessages("sum1").toString()).doesNotContain("mensaje número 1");
        assertThat(sessionService.getMessages("sum1").toString()).contains("mensaje número 1"); // el log completo sigue
    }

    @Test
    void verYBorrarUnaSesion() throws Exception {
        send("/persistent/del1", "hola");
        mvc.perform(get("/sessions/del1/view")).andExpect(status().isOk());

        mvc.perform(delete("/sessions/del1")).andExpect(status().isNoContent());
        assertThat(sessionService.findById("del1")).isNull();
    }
}
