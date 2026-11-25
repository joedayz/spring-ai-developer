package com.joedayz.springai.m09;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.openai.core.http.Headers;
import com.openai.errors.RateLimitException;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.moderation.Moderation;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResponse;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = { "spring.ai.openai.api-key=test-key", "acme.budget.daily-tokens=100" })
@AutoConfigureMockMvc
class FaqControllerTest {

    @Autowired MockMvc mvc;
    @Autowired MeterRegistry meters;
    @MockitoBean ChatModel chatModel;
    @MockitoBean ModerationModel moderationModel;

    @BeforeEach
    void setUp() {
        reset(chatModel, moderationModel);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        moderation(false);
        replyWithTokens(60);
    }

    private void moderation(boolean flagged) {
        var result = ModerationResult.builder().flagged(flagged).build();
        var moderation = Moderation.builder().id("m").model("omni").results(List.of(result)).build();
        when(moderationModel.call(any(ModerationPrompt.class)))
                .thenReturn(new ModerationResponse(new org.springframework.ai.moderation.Generation(moderation)));
    }

    private void replyWithTokens(int total) {
        var metadata = ChatResponseMetadata.builder().usage(new DefaultUsage(total - 10, 10, total)).build();
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("respuesta del modelo"))), metadata));
    }

    /** Cada prueba usa preguntas y usuarios únicos para no compartir caché ni presupuesto. */
    private static String unique(String text) {
        return text + " " + UUID.randomUUID();
    }

    private ResultActions ask(String user, String question) throws Exception {
        return mvc.perform(post("/faq").header("X-User", user).contentType(MediaType.TEXT_PLAIN).content(question));
    }

    private double counter(String name, String... tags) {
        var c = meters.find(name).tags(tags).counter();
        return c == null ? 0 : c.count();
    }

    @Test
    void respondeConElModeloYUsaLosHechosDelSystemPrompt() throws Exception {
        ask("u1", unique("¿Cuánto cuesta el envío?")).andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("respuesta del modelo"))
                .andExpect(jsonPath("$.source").value("model"));

        var captor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        assertThat(captor.getValue().getSystemMessage().getText()).contains("<hechos>", "25 soles");
    }

    @Test
    void moderacionBloqueaSinLlamarAlModeloDeChat() throws Exception {
        moderation(true);
        double before = counter("acme.ai.moderation.blocked");

        ask("u2", unique("texto ofensivo")).andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(ModerationAdvisor.BLOCKED_REPLY))
                .andExpect(jsonPath("$.source").value("moderation"));

        verify(chatModel, never()).call(any(Prompt.class));
        assertThat(counter("acme.ai.moderation.blocked")).isEqualTo(before + 1);
    }

    @Test
    void laCacheEvitaLaSegundaLlamadaYNoConsumePresupuesto() throws Exception {
        String id = UUID.randomUUID().toString();
        ask("u3", "¿Hacen envíos internacionales? " + id).andExpect(jsonPath("$.source").value("model"));
        ask("u4", "  HACEN ENVIOS INTERNACIONALES " + id.toUpperCase() + "?? ")   // mayúsculas, sin tildes, espacios
                .andExpect(jsonPath("$.source").value("cache"));

        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void elPresupuestoDiarioDevuelve429AlAgotarse() throws Exception {
        double before = counter("acme.ai.budget.rejected");
        ask("u5", unique("pregunta 1")).andExpect(status().isOk()); // 60 tokens
        ask("u5", unique("pregunta 2")).andExpect(status().isOk()); // 120, supera 100
        ask("u5", unique("pregunta 3")).andExpect(status().isTooManyRequests());
        ask("u6", unique("pregunta 3")).andExpect(status().isOk()); // otro usuario no se ve afectado

        assertThat(counter("acme.ai.budget.rejected")).isEqualTo(before + 1);
    }

    @Test
    void preguntaDemasiadoLargaSeRechazaSinLlamarAlModelo() throws Exception {
        ask("u7", "a".repeat(FaqController.MAX_QUESTION_CHARS + 1)).andExpect(status().isBadRequest());
        verify(moderationModel, never()).call(any(ModerationPrompt.class));
    }

    @Test
    void siOpenAiEstaSaturadoRespondeUn503Claro() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(RateLimitException.builder().headers(Headers.builder().build()).build());

        ask("u8", unique("hola")).andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("saturado")));
    }

    @Test
    void lasMetricasSeExponenEnPrometheus() throws Exception {
        ask("u9", unique("hola")).andExpect(status().isOk());

        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("acme_ai_cache_total")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("acme_ai_tokens_total")));
    }
}
