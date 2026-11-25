package com.joedayz.springai.m09;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.core.Ordered;

/**
 * Básico/Intermedio: revisa la entrada con el modelo de moderación (gratuito en OpenAI) ANTES de gastar en el chat.
 * Si el texto se marca, corta la cadena y responde un mensaje fijo: el modelo de chat nunca lo ve.
 */
class ModerationAdvisor implements CallAdvisor {

    static final String BLOCKED_REPLY = "No puedo ayudarte con ese mensaje. Si necesitas soporte, escribe a soporte@acme.test.";

    private final ModerationModel moderationModel;
    private final MeterRegistry meters;

    ModerationAdvisor(ModerationModel moderationModel, MeterRegistry meters) {
        this.moderationModel = moderationModel;
        this.meters = meters;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String text = Replies.userText(request);
        boolean flagged = !text.isBlank() && moderationModel.call(new ModerationPrompt(text))
                .getResult().getOutput().getResults().stream().anyMatch(r -> r.isFlagged());
        if (flagged) {
            meters.counter("acme.ai.moderation.blocked").increment();
            return Replies.of(request, BLOCKED_REPLY, "moderation");
        }
        return chain.nextCall(request);
    }

    @Override
    public String getName() {
        return "moderation";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 50;
    }
}
