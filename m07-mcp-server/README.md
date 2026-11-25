# Módulo 7a: Servidor MCP

## Teoría
- **MCP (Model Context Protocol)**: protocolo abierto para que una aplicación de IA descubra y use capacidades externas.
  Un servidor MCP se escribe una vez y lo usan muchos clientes: Spring AI, Claude Desktop, IDEs, MCP Inspector.
- Un servidor expone tres cosas:
  - **Tools** (`@McpTool`): acciones que el modelo decide invocar.
  - **Resources** (`@McpResource`): datos que el cliente lee como contexto, identificados por URI (`policy://envios`).
  - **Prompts** (`@McpPrompt`): plantillas reutilizables que el cliente ofrece al usuario.
- **Transportes**: STDIO (proceso local, sin red), Streamable HTTP (el recomendado para red) y Stateless.
  SSE está deprecado desde Spring AI 2.0.
- **Errores**: si una tool lanza excepción, el cliente recibe un resultado con `isError=true` y el mensaje.
- **Seguridad**: el transporte HTTP **no autentica nada**. Sin una capa de seguridad, cualquiera que alcance `/mcp`
  puede listar e invocar todas tus tools. Además, una página web abierta en tu navegador puede llamar a un servidor en
  localhost (DNS rebinding). Este módulo aplica dos controles en `McpSecurityFilter`:
  token Bearer (comparado en tiempo constante) y validación del header `Origin`. También escucha solo en `127.0.0.1`.
  En producción usa Spring Security con OAuth2 o el proyecto MCP Security.

## Ejecutar
```bash
export ACME_MCP_TOKEN=$(openssl rand -hex 24)   # guárdalo: el cliente necesita el mismo valor
mvn -pl m07-mcp-server spring-boot:run          # http://127.0.0.1:8087/mcp
```
Probar con MCP Inspector (requiere Node): `npx @modelcontextprotocol/inspector`, transporte *Streamable HTTP*,
URL `http://127.0.0.1:8087/mcp` y cabecera `Authorization: Bearer <token>`.

Pruebas (cliente MCP real contra el servidor en un puerto aleatorio): `mvn -pl m07-mcp-server test`.

## Ejercicios
**Básico**
1. Agrega una tool `list_categories` sin parámetros y verifícala con MCP Inspector.
2. Quita la `description` de `search_products` y observa cómo cambia la elección de tools en el cliente (módulo 7b).

**Intermedio**
3. Agrega un resource `catalog://products` que devuelva el catálogo completo en JSON (`mimeType = "application/json"`).
4. Agrega un argumento `tone` al prompt `customer_reply` (formal o cercano).
5. Lanza una excepción con datos internos (SQL, rutas) y comprueba que llega al cliente. Decide qué mensaje es seguro exponer.

**Avanzado**
6. Reemplaza `McpSecurityFilter` por Spring Security con un `SecurityFilterChain` y tokens JWT (`spring-boot-starter-oauth2-resource-server`).
7. Da permisos por tool: un token de solo lectura que no pueda llamar a una futura tool `update_price`.
8. Empaqueta el servidor como STDIO (`spring.ai.mcp.server.stdio=true`) y regístralo en Claude Desktop o en tu IDE.
