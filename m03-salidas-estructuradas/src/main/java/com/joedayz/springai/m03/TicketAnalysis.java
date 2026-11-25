package com.joedayz.springai.m03;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Resultado estructurado que esperamos del modelo. */
public record TicketAnalysis(
        @NotNull @JsonPropertyDescription("Categoría del ticket") Category category,
        @NotNull @JsonPropertyDescription("Prioridad según urgencia e impacto") Priority priority,
        @NotBlank @Size(max = 200) @JsonPropertyDescription("Resumen de una frase") String summary,
        @NotEmpty @JsonPropertyDescription("Acciones sugeridas, de la más a la menos importante") List<String> suggestedActions) {

    public enum Category { BILLING, TECHNICAL, ACCOUNT, OTHER }

    public enum Priority { LOW, MEDIUM, HIGH, CRITICAL }
}
