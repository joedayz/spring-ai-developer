package com.joedayz.springai.m02;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
class SupportController {

    private final ChatClient chatClient;

    SupportController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /** Básico: system prompt por defecto + mensaje del usuario. */
    @GetMapping("/ask")
    String ask(@RequestParam String question) {
        return chatClient.prompt().user(question).call().content();
    }

    /** Básico: plantilla con variables, usando placeholders {producto} y {problema}. */
    @GetMapping("/ticket")
    String ticket(@RequestParam String producto, @RequestParam String problema) {
        var template = new PromptTemplate(
                "Redacta una respuesta breve para un cliente que tiene este problema "
                        + "con el producto {producto}: {problema}");
        return chatClient.prompt(template.create(Map.of("producto", producto, "problema", problema)))
                .call()
                .content();
    }

    /** Intermedio: streaming por Server-Sent Events. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<String> stream(@RequestParam String question) {
        return chatClient.prompt().user(question).stream().content();
    }

    /** Intermedio: opciones distintas por request (temperatura y tope de tokens). */
    @GetMapping("/creative")
    String creative(@RequestParam String question,
                    @RequestParam(defaultValue = "1.0") double temperature) {
        return chatClient.prompt()
                .user(question)
                .options(OpenAiChatOptions.builder()
                        .temperature(temperature)
                        .maxCompletionTokens(300))
                .call()
                .content();
    }

    /** Avanzado: respuesta completa con metadata de uso de tokens. */
    @GetMapping("/usage")
    Map<String, Object> usage(@RequestParam String question) {
        var response = chatClient.prompt().user(question).call().chatResponse();
        var usage = response.getMetadata().getUsage();
        return Map.of(
                "answer", response.getResult().getOutput().getText(),
                "model", response.getMetadata().getModel(),
                "promptTokens", usage.getPromptTokens(),
                "completionTokens", usage.getCompletionTokens(),
                "totalTokens", usage.getTotalTokens());
    }
}
