package com.joedayz.springai.m06;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/**
 * Guarda las pausas esperando decisión. En la demo es memoria del proceso; en producción
 * debe ser una tabla o Redis con expiración. La respuesta pausada NUNCA se toma del cliente.
 */
@Component
class PendingActions {

    record Pending(String customerId, List<Message> conversation, ChatResponse reply) { }

    private final Map<String, Pending> store = new ConcurrentHashMap<>();

    String save(Pending pending) {
        String id = UUID.randomUUID().toString();
        store.put(id, pending);
        return id;
    }

    /**
     * Devuelve y elimina la pausa solo si pertenece al cliente indicado. Eliminarla evita que una misma
     * decisión se ejecute dos veces (anti-replay), y comprobar el dueño antes evita que otro cliente
     * consuma o apruebe una pausa ajena.
     */
    Optional<Pending> take(String id, String customerId) {
        Pending pending = store.get(id);
        if (pending == null || !pending.customerId().equals(customerId)) {
            return Optional.empty();
        }
        return store.remove(id, pending) ? Optional.of(pending) : Optional.empty();
    }
}
