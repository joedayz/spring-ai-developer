package com.joedayz.springai.m03;

import java.util.List;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class TicketController {

    private final ChatClient chatClient;
    private final Validator validator;

    TicketController(ChatClient.Builder builder, Validator validator) {
        this.chatClient = builder
                .defaultSystem("Eres un analista de tickets de soporte. Responde solo con los datos pedidos.")
                .build();
        this.validator = validator;
    }

    /** Básico: mapear la respuesta a un record. */
    @PostMapping(path = "/classify", consumes = MediaType.TEXT_PLAIN_VALUE)
    TicketAnalysis classify(@RequestBody String ticket) {
        return chatClient.prompt()
                .user("Analiza este ticket:\n" + ticket)
                .call()
                .entity(TicketAnalysis.class);
    }

    /** Intermedio: lista de objetos (dentro de un envoltorio) y validación con Bean Validation. */
    @PostMapping(path = "/products", consumes = MediaType.TEXT_PLAIN_VALUE)
    List<ProductMention> products(@RequestBody String review) {
        ProductMentions result = chatClient.prompt()
                .user("Extrae los productos mencionados y el sentimiento hacia cada uno:\n" + review)
                .call()
                .entity(ProductMentions.class);
        result.products().forEach(this::validateOrFail);
        return result.products();
    }

    /** Avanzado: reintenta automáticamente si el JSON no cumple el esquema. */
    @PostMapping(path = "/classify-robust", consumes = MediaType.TEXT_PLAIN_VALUE)
    TicketAnalysis classifyRobust(@RequestBody String ticket) {
        var validation = StructuredOutputValidationAdvisor.builder()
                .outputType(TicketAnalysis.class)
                .maxRepeatAttempts(2)
                .build();
        TicketAnalysis result = chatClient.prompt()
                .advisors(validation)
                .user("Analiza este ticket:\n" + ticket)
                .call()
                .entity(TicketAnalysis.class);
        validateOrFail(result);
        return result;
    }

    private <T> void validateOrFail(T value) {
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .reduce((a, b) -> a + "; " + b).orElse("");
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Salida inválida del modelo: " + detail);
        }
    }
}
