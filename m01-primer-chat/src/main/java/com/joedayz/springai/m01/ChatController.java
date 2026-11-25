package com.joedayz.springai.m01;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ChatController {

    private final ChatClient chatClient;

    ChatController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/chat")
    String chat(@RequestParam(defaultValue = "Explica qué es Spring AI en una frase") String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
