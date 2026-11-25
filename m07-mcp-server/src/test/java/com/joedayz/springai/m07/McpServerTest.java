package com.joedayz.springai.m07;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "acme.mcp.token=" + McpServerTest.TOKEN)
class McpServerTest {

    static final String TOKEN = "token-de-prueba-123456";

    @LocalServerPort int port;
    McpSyncClient client;

    private McpSyncClient connect(String token) {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .httpRequestCustomizer((builder, method, uri, body, ctx) -> builder.header("Authorization", "Bearer " + token))
                .build();
        client = McpClient.sync(transport).build();
        client.initialize();
        return client;
    }

    @AfterEach
    void close() {
        if (client != null) {
            client.closeGracefully();
        }
    }

    private static String text(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().getFirst()).text();
    }

    @Test
    void exponeLasToolsConSuDescripcionYEsquema() {
        var tools = connect(TOKEN).listTools().tools();

        assertThat(tools).extracting(McpSchema.Tool::name).containsExactlyInAnyOrder("search_products", "check_stock");
        var search = tools.stream().filter(t -> t.name().equals("search_products")).findFirst().orElseThrow();
        assertThat(search.description()).contains("catálogo");
        assertThat(search.inputSchema().get("required")).asString().contains("query");
    }

    @Test
    void ejecutaUnaTool() {
        var result = connect(TOKEN).callTool(new McpSchema.CallToolRequest("search_products", Map.of("query", "monitor")));

        assertThat(result.isError()).isFalse();
        assertThat(text(result)).contains("MON-24", "MON-27").doesNotContain("AUD-01");
    }

    @Test
    void unErrorDeLaToolLlegaAlClienteComoResultadoDeError() {
        var result = connect(TOKEN).callTool(new McpSchema.CallToolRequest("check_stock", Map.of("sku", "NOPE")));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("No existe el SKU NOPE");
    }

    @Test
    void exponeResourcesYPrompts() {
        var mcp = connect(TOKEN);

        var policy = mcp.readResource(new McpSchema.ReadResourceRequest("policy://envios"));
        assertThat(((McpSchema.TextResourceContents) policy.contents().getFirst()).text()).contains("25 soles");

        var prompt = mcp.getPrompt(new McpSchema.GetPromptRequest("customer_reply", Map.of("issue", "pedido tarde")));
        assertThat(prompt.messages().toString()).contains("pedido tarde");
    }

    @Test
    void sinTokenValidoNoSePuedeConectar() {
        assertThatThrownBy(() -> connect("token-incorrecto-000000")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void rechazaPeticionesSinTokenYConOriginExterno() throws Exception {
        var http = HttpClient.newHttpClient();
        var body = HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
        var base = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream");

        var noToken = http.send(base.copy().POST(body).build(), HttpResponse.BodyHandlers.discarding());
        assertThat(noToken.statusCode()).isEqualTo(401);

        var evilOrigin = http.send(base.copy().header("Authorization", "Bearer " + TOKEN)
                .header("Origin", "https://evil.example").POST(body).build(), HttpResponse.BodyHandlers.discarding());
        assertThat(evilOrigin.statusCode()).isEqualTo(403);
    }
}
