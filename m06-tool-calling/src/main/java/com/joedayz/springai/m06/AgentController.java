package com.joedayz.springai.m06;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallDecision;
import org.springframework.ai.model.tool.ToolCallDecisions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class AgentController {

    record ChatRequest(String message) { }

    record Decision(boolean approve) { }

    record PendingCall(String key, String tool, String arguments) { }

    record AgentReply(String status, String answer, String pendingId, List<PendingCall> calls) {
        static AgentReply done(String answer) {
            return new AgentReply("DONE", answer, null, List.of());
        }
    }

    private final ChatClient chatClient;
    private final ToolCallingManager toolCallingManager;
    private final PendingActions pendingActions;
    private final OrderTools orderTools;
    private final DateTimeTools dateTimeTools;

    AgentController(ChatClient.Builder builder, ToolCallingManager toolCallingManager,
                    PendingActions pendingActions, OrderTools orderTools, DateTimeTools dateTimeTools) {
        this.chatClient = builder.defaultSystem("""
                Eres el asistente de pedidos de Acme. Responde en español y de forma breve.
                Usa las herramientas para consultar datos reales. Si una herramienta falla, explica el motivo al cliente.
                Nunca inventes datos de pedidos.
                """).build();
        this.toolCallingManager = toolCallingManager;
        this.pendingActions = pendingActions;
        this.orderTools = orderTools;
        this.dateTimeTools = dateTimeTools;
    }

    /**
     * El cliente se identifica con la cabecera X-Customer (en producción vendría del token autenticado).
     * Si el modelo pide una tool que requiere confirmación, la respuesta es AWAITING_CONFIRMATION.
     */
    @PostMapping("/agent/chat")
    AgentReply chat(@RequestHeader("X-Customer") String customer, @RequestBody ChatRequest request) {
        List<Message> conversation = List.of(new UserMessage(request.message()));

        ChatResponse reply = chatClient.prompt()
                .messages(conversation)
                .tools(orderTools, dateTimeTools)
                .toolContext(context(customer))
                .call()
                .chatResponse();

        if (!ToolCallDecisions.isAwaitingDecision(reply)) {
            return AgentReply.done(reply.getResult().getOutput().getText());
        }

        AssistantMessage message = reply.getResult().getOutput();
        List<String> keys = ToolCallDecisions.toolCallKeys(message);
        List<PendingCall> calls = new java.util.ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            var toolCall = message.getToolCalls().get(i);
            calls.add(new PendingCall(keys.get(i), toolCall.name(), toolCall.arguments()));
        }
        String pendingId = pendingActions.save(new PendingActions.Pending(customer, conversation, reply));
        return new AgentReply("AWAITING_CONFIRMATION", null, pendingId, calls);
    }

    /** La persona aprueba o rechaza. Solo el mismo cliente puede decidir y solo una vez. */
    @PostMapping("/agent/confirm/{pendingId}")
    AgentReply confirm(@RequestHeader("X-Customer") String customer, @PathVariable String pendingId,
                       @RequestBody Decision decision) {
        var pending = pendingActions.take(pendingId, customer)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Acción pendiente no encontrada"));

        var options = ToolCallingChatOptions.builder()
                .toolCallbacks(ToolCallbacks.from(orderTools, dateTimeTools))
                .toolContext(context(customer))
                .build();

        // Debe haber una decisión por cada tool call de la respuesta pausada.
        var decisions = new ToolCallDecisions(ToolCallDecisions.toolCallKeys(pending.reply().getResult().getOutput())
                .stream()
                .map(key -> decision.approve()
                        ? ToolCallDecision.allow(key)
                        : ToolCallDecision.deny(key, "El cliente rechazó la acción. No la repitas."))
                .toList());

        var result = toolCallingManager.executeToolCalls(
                new Prompt(pending.conversation(), options), pending.reply(), decisions);

        String answer = chatClient.prompt()
                .messages(result.conversationHistory())
                .tools(orderTools, dateTimeTools)
                .toolContext(context(customer))
                .call()
                .content();
        return AgentReply.done(answer);
    }

    private static Map<String, Object> context(String customer) {
        return Map.of(OrderTools.CUSTOMER_ID, customer);
    }
}
