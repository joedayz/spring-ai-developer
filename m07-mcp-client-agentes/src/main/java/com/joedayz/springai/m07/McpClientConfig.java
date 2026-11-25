package com.joedayz.springai.m07;

import java.net.URI;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class McpClientConfig {

    /**
     * Agrega el token a las peticiones HTTP del cliente MCP. Este customizer se aplica a TODAS las
     * conexiones streamable-http, así que solo envía el token al servidor de Acme: si mañana agregas
     * otro servidor MCP, no le filtras la credencial.
     */
    @Bean
    McpSyncHttpClientRequestCustomizer acmeTokenCustomizer(
            @Value("${spring.ai.mcp.client.streamable-http.connections.acme.url}") String acmeUrl,
            @Value("${acme.mcp.token}") String token) {
        URI acme = URI.create(acmeUrl);
        return (builder, method, uri, body, context) -> {
            if (sameServer(acme, uri)) {
                builder.header("Authorization", "Bearer " + token);
            }
        };
    }

    static boolean sameServer(URI a, URI b) {
        return a.getScheme().equalsIgnoreCase(b.getScheme())
                && a.getHost().equalsIgnoreCase(b.getHost())
                && a.getPort() == b.getPort();
    }
}
