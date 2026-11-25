package com.joedayz.springai.m09;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class FaqController {

    static final int MAX_QUESTION_CHARS = 500;

    private final ChatClient faqClient;

    FaqController(ChatClient faqClient) {
        this.faqClient = faqClient;
    }

    /** El usuario llega por cabecera para la demo; en producción sale del token autenticado. */
    @PostMapping(path = "/faq", consumes = MediaType.TEXT_PLAIN_VALUE)
    Map<String, Object> faq(@RequestHeader("X-User") String user, @RequestBody String question) {
        if (question.isBlank() || question.length() > MAX_QUESTION_CHARS) {
            // Limitar la entrada limita el costo y la superficie de prompt injection.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La pregunta debe tener entre 1 y " + MAX_QUESTION_CHARS + " caracteres");
        }
        var response = faqClient.prompt()
                .user(question)
                .advisors(a -> a.params(Replies.userParam(user)))
                .call()
                .chatResponse();
        Object source = response.getMetadata().get(Replies.SOURCE);
        return Map.of("answer", response.getResult().getOutput().getText(),
                "source", source == null ? "model" : source);
    }
}
