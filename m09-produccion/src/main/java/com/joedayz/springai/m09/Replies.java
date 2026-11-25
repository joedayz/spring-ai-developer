package com.joedayz.springai.m09;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/** Construye una respuesta sin llamar al modelo (para cortar la cadena de advisors). */
final class Replies {

    static final String SOURCE = "acme.source";

    private Replies() {
    }

    static ChatClientResponse of(ChatClientRequest request, String text, String source) {
        var metadata = ChatResponseMetadata.builder().keyValue(SOURCE, source).build();
        var chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadata);
        return ChatClientResponse.builder().chatResponse(chatResponse).context(request.context()).build();
    }

    static String userText(ChatClientRequest request) {
        var user = request.prompt().getUserMessage();
        return user == null || user.getText() == null ? "" : user.getText();
    }

    static String param(ChatClientRequest request, String key) {
        Object value = request.context().get(key);
        return value == null ? null : value.toString();
    }

    static Map<String, Object> userParam(String user) {
        return Map.of(TokenBudgetAdvisor.USER, user);
    }
}
