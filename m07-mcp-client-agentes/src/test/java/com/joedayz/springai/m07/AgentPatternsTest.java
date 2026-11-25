package com.joedayz.springai.m07;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "spring.ai.mcp.client.enabled=false",
        "acme.mcp.token=no-se-usa-en-pruebas" })
@AutoConfigureMockMvc
class AgentPatternsTest {

    static final String OK_DRAFT = "Lamentamos la demora. Revisa Mi cuenta > Mis pedidos o escríbenos a soporte@acme.test.";

    @Autowired MockMvc mvc;
    @MockitoBean ChatModel chatModel;

    /** Todos los prompts que recibió el modelo, en texto. */
    final List<String> prompts = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        reset(chatModel);
        prompts.clear();
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
    }

    private void model(Function<String, String> reply) {
        when(chatModel.call(any(Prompt.class))).thenAnswer(inv -> {
            String text = inv.<Prompt>getArgument(0).getInstructions().toString();
            prompts.add(text);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply.apply(text)))));
        });
    }

    private org.springframework.test.web.servlet.ResultActions send(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.TEXT_PLAIN).content(body));
    }

    @Test
    void chainPasaLaSalidaDeCadaPasoAlSiguiente() throws Exception {
        model(p -> p.contains("Extrae") ? "HECHOS-1" : p.contains("resumen de 2 frases") ? "RESUMEN-2" : "RESPUESTA-3");

        send("/agents/chain", "Mi monitor llegó roto").andExpect(status().isOk())
                .andExpect(jsonPath("$[2]").value("RESPUESTA-3"));

        assertThat(prompts).hasSize(3);
        assertThat(prompts.get(1)).contains("HECHOS-1");
        assertThat(prompts.get(2)).contains("RESUMEN-2");
    }

    @Test
    void routeEnviaElMensajeAlEspecialistaElegido() throws Exception {
        model(p -> p.contains("Clasifica") ? "{\"route\":\"BILLING\",\"reason\":\"cobro\"}" : "respuesta de facturación");

        send("/agents/route", "Me cobraron dos veces").andExpect(status().isOk())
                .andExpect(jsonPath("$.route").value("BILLING"))
                .andExpect(jsonPath("$.answer").value("respuesta de facturación"));

        assertThat(prompts.get(1)).contains("área de facturación").doesNotContain("soporte técnico");
    }

    @Test
    void orchestrateLimitaLasSubtareasYLasEjecutaTodas() throws Exception {
        String plan = """
                {"analysis":"lanzamiento","subtasks":[
                 {"type":"correo","instruction":"a"},{"type":"post","instruction":"b"},
                 {"type":"banner","instruction":"c"},{"type":"sms","instruction":"d"},
                 {"type":"extra","instruction":"e"}]}""";
        model(p -> p.contains("Divide") ? plan : "entregable");

        send("/agents/orchestrate", "Lanzamiento del monitor 27").andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis").value("lanzamiento"))
                .andExpect(jsonPath("$.results.length()").value(4));

        assertThat(prompts).hasSize(5); // 1 plan + 4 trabajadores (la quinta subtarea se descarta)
    }

    @Test
    void refineReintentaConElFeedbackHastaAprobar() throws Exception {
        var evaluations = new java.util.concurrent.atomic.AtomicInteger();
        model(p -> {
            if (p.contains("revisor de respuestas")) {
                return evaluations.incrementAndGet() == 1
                        ? "{\"friendly\":false,\"noUnfoundedPromises\":true,\"feedback\":\"TONO-FRIO\"}"
                        : "{\"friendly\":true,\"noUnfoundedPromises\":true,\"feedback\":\"\"}";
            }
            return OK_DRAFT;
        });

        send("/agents/refine", "Responde a un cliente molesto por demora").andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true))
                .andExpect(jsonPath("$.iterations").value(2))
                .andExpect(jsonPath("$.feedback[0]").value("TONO-FRIO"));

        assertThat(prompts.get(2)).contains("TONO-FRIO"); // el segundo borrador recibe el feedback
    }

    @Test
    void refineSeDetieneAlAgotarLosIntentos() throws Exception {
        model(p -> p.contains("revisor de respuestas")
                ? "{\"friendly\":false,\"noUnfoundedPromises\":false,\"feedback\":\"mal\"}" : OK_DRAFT);

        send("/agents/refine?maxIterations=2", "x").andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(false))
                .andExpect(jsonPath("$.iterations").value(2));

        assertThat(prompts).hasSize(4);
    }

    @Test
    void sinServidoresMcpElAsistenteArrancaSinTools() throws Exception {
        mvc.perform(get("/assistant/tools")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void refineRechazaEnCodigoLoVerificableSinLlamarAlEvaluador() throws Exception {
        var drafts = new java.util.concurrent.atomic.AtomicInteger();
        model(p -> {
            if (p.contains("revisor de respuestas")) {
                return "{\"friendly\":true,\"noUnfoundedPromises\":true,\"feedback\":\"\"}";
            }
            return switch (drafts.incrementAndGet()) {
                case 1 -> "palabra ".repeat(80);                       // demasiado larga
                case 2 -> "Escríbenos a [correo] y te ayudamos.";        // placeholder
                case 3 -> "Lamentamos la demora, gracias por tu paciencia."; // sin canal
                default -> OK_DRAFT;
            };
        });

        send("/agents/refine?maxIterations=5", "x").andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true))
                .andExpect(jsonPath("$.iterations").value(4))
                .andExpect(jsonPath("$.feedback[0]").value(org.hamcrest.Matchers.containsString("80 palabras")))
                .andExpect(jsonPath("$.feedback[1]").value(org.hamcrest.Matchers.containsString("placeholder")))
                .andExpect(jsonPath("$.feedback[2]").value(org.hamcrest.Matchers.containsString("canal real")));

        assertThat(prompts).hasSize(5); // 4 borradores y una sola evaluación del modelo
    }
}
