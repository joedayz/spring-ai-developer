package com.joedayz.springai.m03;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.Valid;

/**
 * Envoltorio para listas: OpenAI exige que la raíz del JSON sea un objeto, no un arreglo.
 * Si pides directamente {@code List<X>} el modelo puede devolver un objeto y la deserialización falla.
 */
public record ProductMentions(
        @Valid @JsonPropertyDescription("Productos mencionados en el texto") List<ProductMention> products) { }
