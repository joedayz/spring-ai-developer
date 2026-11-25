package com.joedayz.springai.m07;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * Capacidades que el servidor expone por MCP. Cualquier cliente MCP (Spring AI, Claude Desktop,
 * MCP Inspector, IDEs) puede descubrirlas y usarlas sin conocer este código.
 */
@Component
class CatalogTools {

    record Product(String sku, String name, String category, double price, int stock) { }

    private static final List<Product> CATALOG = List.of(
            new Product("AUD-01", "Audífonos Bluetooth", "audio", 120.0, 14),
            new Product("AUD-02", "Parlante portátil", "audio", 180.0, 0),
            new Product("TEC-01", "Teclado mecánico", "perifericos", 250.0, 7),
            new Product("MON-24", "Monitor 24 pulgadas", "monitores", 640.0, 3),
            new Product("MON-27", "Monitor 27 pulgadas", "monitores", 890.0, 5));

    private static final Map<String, String> POLICIES = Map.of(
            "devoluciones", "30 días calendario desde la entrega, producto sin uso y en su empaque original.",
            "envios", "Estándar 3 a 5 días hábiles, gratis desde 150 soles. Exprés 24 horas en Lima por 25 soles.",
            "garantia", "12 meses para electrónicos y 3 meses para accesorios. Cubre defectos de fabricación.");

    /** Básico: una tool de solo lectura. */
    @McpTool(name = "search_products", description = "Busca productos del catálogo de Acme por texto o categoría")
    List<Product> searchProducts(
            @McpToolParam(description = "Texto a buscar en el nombre o la categoría", required = true) String query) {
        String q = query.toLowerCase(Locale.ROOT);
        return CATALOG.stream()
                .filter(p -> p.name().toLowerCase(Locale.ROOT).contains(q) || p.category().contains(q))
                .toList();
    }

    @McpTool(name = "check_stock", description = "Devuelve el stock disponible de un producto por su SKU")
    Map<String, Object> checkStock(@McpToolParam(description = "SKU del producto, por ejemplo MON-24", required = true)
                                   String sku) {
        return CATALOG.stream()
                .filter(p -> p.sku().equalsIgnoreCase(sku))
                .findFirst()
                .<Map<String, Object>>map(p -> Map.of("sku", p.sku(), "stock", p.stock(), "available", p.stock() > 0))
                .orElseThrow(() -> new IllegalArgumentException("No existe el SKU " + sku));
    }

    /** Intermedio: un resource. El cliente lo lee como contexto, el modelo no lo "ejecuta". */
    @McpResource(uri = "policy://{name}", name = "Política de Acme", mimeType = "text/plain",
            description = "Políticas de Acme: devoluciones, envios, garantia")
    String policy(String name) {
        String text = POLICIES.get(name);
        if (text == null) {
            throw new IllegalArgumentException("Política desconocida: " + name);
        }
        return text;
    }

    /** Intermedio: un prompt reutilizable que el cliente puede ofrecer al usuario. */
    @McpPrompt(name = "customer_reply", description = "Plantilla para responder a un cliente de Acme")
    String customerReply(@McpArg(name = "issue", description = "Problema del cliente", required = true) String issue) {
        return "Redacta una respuesta breve, amable y en español para un cliente de Acme con este problema: " + issue
                + ". Usa las herramientas del catálogo si necesitas datos de productos.";
    }
}
