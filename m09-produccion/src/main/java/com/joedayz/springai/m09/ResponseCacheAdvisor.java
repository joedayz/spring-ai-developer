package com.joedayz.springai.m09;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.core.Ordered;

/**
 * Intermedio: caché de respuestas para preguntas frecuentes. Un acierto no llama al modelo ni consume presupuesto.
 * Úsalo SOLO en endpoints cuya respuesta no dependa del usuario (FAQ). En un chat con datos personales,
 * una caché compartida filtraría la respuesta de un usuario a otro.
 * La clave incluye el system prompt: si cambias las instrucciones, no se sirven respuestas viejas.
 */
class ResponseCacheAdvisor implements CallAdvisor {

    private record Cached(String text, Instant expiresAt) { }

    private final Map<String, Cached> cache;
    private final Duration ttl;
    private final Clock clock;
    private final MeterRegistry meters;

    ResponseCacheAdvisor(int maxEntries, Duration ttl, Clock clock, MeterRegistry meters) {
        this.ttl = ttl;
        this.clock = clock;
        this.meters = meters;
        this.cache = java.util.Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
                return size() > maxEntries; // LRU: limita la memoria
            }
        });
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String key = key(request);
        Cached hit = cache.get(key);
        if (hit != null && hit.expiresAt().isAfter(clock.instant())) {
            meters.counter("acme.ai.cache", "result", "hit").increment();
            return Replies.of(request, hit.text(), "cache");
        }
        meters.counter("acme.ai.cache", "result", "miss").increment();
        ChatClientResponse response = chain.nextCall(request);
        var chatResponse = response.chatResponse();
        if (chatResponse != null && chatResponse.getResult() != null
                && chatResponse.getMetadata().get(Replies.SOURCE) == null) { // no cachear respuestas de bloqueo
            cache.put(key, new Cached(chatResponse.getResult().getOutput().getText(), clock.instant().plus(ttl)));
        }
        return response;
    }

    static String key(ChatClientRequest request) {
        var system = request.prompt().getSystemMessage();
        String noAccents = java.text.Normalizer.normalize(Replies.userText(request), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", ""); // "envío exprés" y "envio expres" son la misma pregunta
        String normalized = noAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[¿?¡!.,;:]", " ")   // signos en cualquier posición
                .strip().replaceAll("\\s+", " ");
        return (system == null ? "" : system.getText()).hashCode() + "|" + normalized;
    }

    @Override
    public String getName() {
        return "response-cache";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 60;
    }
}
