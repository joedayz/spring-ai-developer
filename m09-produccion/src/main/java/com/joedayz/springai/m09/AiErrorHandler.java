package com.joedayz.springai.m09;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Avanzado: degradación controlada. Los reintentos y el timeout los aplica el cliente de OpenAI
 * (spring.ai.openai.max-retries / timeout). Cuando se agotan, el usuario recibe un 503 claro,
 * sin detalles internos, y el error queda en el log con su causa.
 */
@RestControllerAdvice
class AiErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(AiErrorHandler.class);

    @ExceptionHandler(OpenAIServiceException.class)
    ResponseEntity<ProblemDetail> openAi(OpenAIServiceException e) {
        log.error("OpenAI respondió {}: {}", e.statusCode(), e.getMessage());
        if (e.statusCode() == 401 || e.statusCode() == 403) {
            // Error de configuración nuestro: no es culpa del usuario ni se arregla reintentando.
            return problem(HttpStatus.INTERNAL_SERVER_ERROR, "El asistente no está disponible por un problema de configuración", null);
        }
        if (e.statusCode() == 429 || e.statusCode() >= 500) {
            return problem(HttpStatus.SERVICE_UNAVAILABLE, "El asistente está saturado. Intenta de nuevo en un minuto.", "60");
        }
        return problem(HttpStatus.BAD_GATEWAY, "No pudimos procesar tu consulta", null);
    }

    @ExceptionHandler(OpenAIIoException.class)
    ResponseEntity<ProblemDetail> timeout(OpenAIIoException e) {
        log.error("Sin respuesta de OpenAI: {}", e.getMessage());
        return problem(HttpStatus.GATEWAY_TIMEOUT, "El asistente tardó demasiado. Intenta de nuevo.", null);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail, String retryAfter) {
        var builder = ResponseEntity.status(status);
        if (retryAfter != null) {
            builder.header(HttpHeaders.RETRY_AFTER, retryAfter);
        }
        return builder.body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
