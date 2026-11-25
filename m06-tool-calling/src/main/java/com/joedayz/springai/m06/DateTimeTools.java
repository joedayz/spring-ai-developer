package com.joedayz.springai.m06;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** Básico: el modelo no sabe qué día es hoy. Una tool sin parámetros se lo dice. */
@Component
class DateTimeTools {

    @Tool(description = "Devuelve la fecha y hora actual en la zona horaria de Lima")
    String currentDateTime() {
        return LocalDateTime.now(ZoneId.of("America/Lima")).toString();
    }
}
