package com.joedayz.springai.m07;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Intermedio: un ChatClient que usa las tools de un servidor MCP como si fueran locales. */
@RestController
class AssistantController {

    private final ChatClient chatClient;
    private final List<ToolCallbackProvider> mcpTools;

    AssistantController(ChatClient.Builder builder, ObjectProvider<ToolCallbackProvider> mcpTools) {
        this.mcpTools = mcpTools.orderedStream().toList();
        this.chatClient = builder
                .defaultSystem("Eres el asistente de ventas de Acme. Responde en español y de forma breve. "
                        + "Usa las herramientas para consultar productos y stock; nunca inventes precios ni stock.")
                .defaultToolCallbacks(this.mcpTools.toArray(ToolCallbackProvider[]::new))
                .build();
    }

    @PostMapping(path = "/assistant", consumes = MediaType.TEXT_PLAIN_VALUE)
    String ask(@RequestBody String question) {
        return chatClient.prompt().user(question).call().content();
    }

    /** Qué tools descubrió el cliente en los servidores MCP configurados. */
    @GetMapping("/assistant/tools")
    List<String> tools() {
        return mcpTools.stream()
                .flatMap(p -> java.util.Arrays.stream(p.getToolCallbacks()))
                .map(t -> t.getToolDefinition().name())
                .toList();
    }
}
