package com.joedayz.springai.m07;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Patrones de agentes de "Building effective agents" (Anthropic), que la documentación de Spring AI
 * implementa en sus ejemplos. Son flujos de trabajo con pasos definidos en código: más predecibles,
 * baratos y fáciles de probar que un agente autónomo. Úsalos antes de recurrir a un agente libre.
 */
@Service
class AgentPatterns {

    // ---------- tipos de salida estructurada (siempre con un objeto en la raíz) ----------

    enum Route { BILLING, TECHNICAL, SALES }

    record RouteDecision(@JsonPropertyDescription("Área que debe atender el mensaje") Route route,
                         @JsonPropertyDescription("Motivo en una frase") String reason) { }

    record Subtask(@JsonPropertyDescription("Tipo de entregable, por ejemplo 'correo' o 'post'") String type,
                   @JsonPropertyDescription("Instrucción concreta para el redactor") String instruction) { }

    record Plan(@JsonPropertyDescription("Análisis breve de la tarea") String analysis,
                @JsonPropertyDescription("Entre 2 y 4 subtareas independientes") List<Subtask> subtasks) { }

    record Evaluation(boolean approved, String feedback) { }

    /** Rúbrica: un booleano por criterio es más confiable que un único "aprobado". */
    record Review(@JsonPropertyDescription("¿El tono es amable y empático?") boolean friendly,
                  @JsonPropertyDescription("¿Evita prometer plazos, reembolsos o compensaciones que la solicitud no menciona?")
                  boolean noUnfoundedPromises,
                  @JsonPropertyDescription("Qué corregir, en una frase. Vacío si ambos criterios se cumplen") String feedback) {

        Evaluation toEvaluation() {
            return new Evaluation(friendly && noUnfoundedPromises, feedback);
        }
    }

    record Routed(Route route, String reason, String answer) { }

    record Orchestrated(String analysis, List<String> results) { }

    record Refined(String answer, int iterations, boolean approved, List<String> history, List<String> feedback) { }

    private static final String ROUTER_PROMPT = """
            Clasifica el mensaje de un cliente de Acme en un área:
            BILLING (cobros, facturas, reembolsos), TECHNICAL (fallas, configuración), SALES (precios, stock, compras).
            Mensaje: %s""";

    private static final java.util.Map<Route, String> SPECIALISTS = java.util.Map.of(
            Route.BILLING, "Eres del área de facturación de Acme. Pide número de pedido y explica plazos de reembolso (10 días hábiles).",
            Route.TECHNICAL, "Eres soporte técnico de Acme. Da pasos de diagnóstico numerados y concretos.",
            Route.SALES, "Eres ventas de Acme. Sé entusiasta y breve, y menciona el envío gratis desde 150 soles.");

    static final int MAX_WORDS = 60;

    /** Datos y criterios compartidos: generador y evaluador deben ver exactamente lo mismo. */
    static final String SUPPORT_FACTS = """
            Canales de Acme (no existen otros, no hay teléfono): correo soporte@acme.test y
            seguimiento en Mi cuenta > Mis pedidos.""";

    static final String GENERATOR_RULES = """
            Reglas: máximo %d palabras, tono amable, termina con un siguiente paso concreto usando
            solo los canales indicados, no prometas nada que la solicitud no mencione,
            no uses placeholders entre corchetes.""".formatted(MAX_WORDS);

    private final ChatClient chatClient;
    private final Executor executor;

    AgentPatterns(ChatClient.Builder builder, @Qualifier("applicationTaskExecutor") Executor executor) {
        this.chatClient = builder.build();
        this.executor = executor;
    }

    /** 1. Encadenamiento (chain): cada paso transforma la salida del anterior. */
    List<String> chain(String rawTicket) {
        List<String> steps = List.of(
                "Extrae solo los hechos del siguiente ticket, en viñetas, sin opiniones:\n%s",
                "A partir de estos hechos, redacta un resumen de 2 frases para el equipo interno:\n%s",
                "Convierte este resumen en una respuesta amable para el cliente, en español, máximo 3 frases:\n%s");
        List<String> outputs = new ArrayList<>();
        String current = rawTicket;
        for (String step : steps) {
            current = chatClient.prompt().user(step.formatted(current)).call().content();
            outputs.add(current);
        }
        return outputs;
    }

    /** 2. Enrutamiento (routing): un modelo clasifica y un especialista responde. */
    Routed route(String message) {
        RouteDecision decision = chatClient.prompt()
                .user(ROUTER_PROMPT.formatted(message))
                .call()
                .entity(RouteDecision.class);
        String answer = chatClient.prompt()
                .system(SPECIALISTS.get(decision.route()))
                .user(message)
                .call()
                .content();
        return new Routed(decision.route(), decision.reason(), answer);
    }

    /** 3. Orquestador-trabajadores: un modelo planifica, varios trabajan en paralelo. */
    Orchestrated orchestrate(String task) {
        Plan plan = chatClient.prompt()
                .user("Divide esta tarea de marketing de Acme en subtareas independientes:\n" + task)
                .call()
                .entity(Plan.class);
        List<Subtask> subtasks = plan.subtasks().stream().limit(4).toList(); // límite: no confíes en el modelo
        List<CompletableFuture<String>> futures = subtasks.stream()
                .map(st -> CompletableFuture.supplyAsync(() -> chatClient.prompt()
                        .system("Eres redactor de Acme. Entrega solo el texto final, en español.")
                        .user("Tarea general: " + task + "\nTu entregable (" + st.type() + "): " + st.instruction())
                        .call()
                        .content(), executor))
                .toList();
        return new Orchestrated(plan.analysis(), futures.stream().map(CompletableFuture::join).toList());
    }

    /** 4. Evaluador-optimizador: un modelo genera, otro evalúa, se repite hasta aprobar o agotar intentos. */
    Refined refine(String request, int maxIterations) {
        List<String> history = new ArrayList<>();
        List<String> feedbacks = new ArrayList<>();
        String feedback = "";
        String draft = "";
        for (int i = 1; i <= maxIterations; i++) {
            draft = chatClient.prompt()
                    // Sin los datos, el generador inventa placeholders ("[teléfono]"); sin las reglas,
                    // al corregir un punto rompe otro (por ejemplo, alarga la respuesta).
                    .system("Eres soporte de Acme. Responde en español.\n" + SUPPORT_FACTS + "\n" + GENERATOR_RULES)
                    .user(feedback.isBlank() ? request
                            : request + "\n\nBorrador anterior:\n" + draft + "\n\nCorrige según este feedback:\n" + feedback)
                    .call()
                    .content();
            history.add(draft);
            // Lo que se puede comprobar en código se comprueba en código. Probado con gpt-4o-mini:
            // contaba mal las palabras y decía "falta un siguiente paso" en respuestas que lo tenían.
            String current = draft;
            Evaluation eval = checkInCode(current).orElseGet(() -> review(request, current));
            if (eval.approved()) {
                return new Refined(draft, i, true, history, feedbacks);
            }
            feedback = eval.feedback();
            feedbacks.add(feedback);
        }
        return new Refined(draft, maxIterations, false, history, feedbacks);
    }

    /** Lo que solo un modelo puede juzgar: tono y promesas. Temperatura 0 para que sea más estable. */
    private Evaluation review(String request, String draft) {
        return chatClient.prompt()
                .options(OpenAiChatOptions.builder().temperature(0.0))
                .system("Eres un revisor de respuestas de soporte. Evalúa solo los criterios del esquema.")
                .user("Solicitud:\n" + request + "\n\nRespuesta a evaluar:\n" + draft)
                .call()
                .entity(Review.class)
                .toEvaluation();
    }

    /** Criterios verificables sin modelo: longitud, canal válido y ausencia de placeholders. */
    static java.util.Optional<Evaluation> checkInCode(String draft) {
        int words = draft.trim().split("\\s+").length;
        if (words > MAX_WORDS) {
            return java.util.Optional.of(new Evaluation(false,
                    "Tiene " + words + " palabras; el máximo es " + MAX_WORDS + ". Acórtala."));
        }
        if (draft.matches("(?s).*\\[[^\\]]+\\].*")) {
            return java.util.Optional.of(new Evaluation(false, "Contiene un placeholder entre corchetes. Usa datos reales."));
        }
        if (!draft.contains("soporte@acme.test") && !draft.contains("Mis pedidos")) {
            return java.util.Optional.of(new Evaluation(false,
                    "Falta un siguiente paso con un canal real: soporte@acme.test o Mi cuenta > Mis pedidos."));
        }
        return java.util.Optional.empty();
    }
}
