package com.joedayz.springai.m04;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los endpoints aceptan solo text/plain. Con form-urlencoded (lo que envía {@code curl -d} por defecto)
 * el texto llega codificado (ana%40acme.com) y el PiiMaskingAdvisor no lo reconoce: el dato se filtraría.
 *
 * En la demo el id de sesión viene en la URL. En producción deriva del usuario autenticado
 * (o guarda SessionAttributes.PRINCIPAL_NAME y verifícalo), para que nadie lea la sesión de otro.
 */
@RestController
class ConversationController {

    private final ChatClient inMemory;
    private final ChatClient persistent;
    private final ChatClient safe;
    private final SessionService sessionService;

    ConversationController(@Qualifier("inMemoryClient") ChatClient inMemory,
                           @Qualifier("persistentClient") ChatClient persistent,
                           @Qualifier("safeClient") ChatClient safe,
                           SessionService sessionService) {
        this.inMemory = inMemory;
        this.persistent = persistent;
        this.safe = safe;
        this.sessionService = sessionService;
    }

    @PostMapping(path = "/memory/{id}", consumes = MediaType.TEXT_PLAIN_VALUE)
    String memory(@PathVariable String id, @RequestBody String message) {
        return talk(inMemory, id, message);
    }

    @PostMapping(path = "/persistent/{id}", consumes = MediaType.TEXT_PLAIN_VALUE)
    String persistent(@PathVariable String id, @RequestBody String message) {
        return talk(persistent, id, message);
    }

    @PostMapping(path = "/safe/{id}", consumes = MediaType.TEXT_PLAIN_VALUE)
    String safe(@PathVariable String id, @RequestBody String message) {
        return talk(safe, id, message);
    }

    /** Lo que el modelo verá en el próximo turno (vista activa, tras compactaciones). */
    @GetMapping("/sessions/{id}/view")
    List<Map<String, String>> view(@PathVariable String id) {
        return sessionService.getActiveMessages(id).stream()
                .map(m -> Map.of("type", m.getMessageType().name(), "text", String.valueOf(m.getText())))
                .toList();
    }

    @DeleteMapping("/sessions/{id}")
    ResponseEntity<Void> delete(@PathVariable String id) {
        sessionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static String talk(ChatClient client, String sessionId, String message) {
        return client.prompt()
                .user(message)
                .advisors(SessionAdvisors.session(sessionId))
                .call()
                .content();
    }
}
