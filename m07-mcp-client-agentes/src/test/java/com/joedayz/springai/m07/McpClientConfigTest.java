package com.joedayz.springai.m07;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpRequest;

import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.Test;

class McpClientConfigTest {

    private final McpClientConfig config = new McpClientConfig();

    private String authHeaderFor(String target) {
        var customizer = config.acmeTokenCustomizer("http://127.0.0.1:8087", "secreto-de-prueba-123");
        var builder = HttpRequest.newBuilder(URI.create(target));
        customizer.customize(builder, "POST", URI.create(target), "{}", McpTransportContext.EMPTY);
        return builder.build().headers().firstValue("Authorization").orElse(null);
    }

    @Test
    void enviaElTokenSoloAlServidorDeAcme() {
        assertThat(authHeaderFor("http://127.0.0.1:8087/mcp")).isEqualTo("Bearer secreto-de-prueba-123");
        assertThat(authHeaderFor("http://127.0.0.1:9999/mcp")).isNull();
        assertThat(authHeaderFor("https://otro-servidor.example/mcp")).isNull();
    }
}
