# Curso Spring AI: de inicio a avanzado

Stack: Java 25, Spring Boot 4.2.0, Spring AI 2.1.0, OpenAI. PGVector con Docker en el módulo 5.
Prerrequisito: conocer Spring Boot.

Cada módulo es un proyecto Maven con su `README.md` (teoría, cómo ejecutarlo y ejercicios en tres niveles:
Básico, Intermedio y Avanzado). El proyecto guía es el asistente de soporte de la tienda ficticia Acme.

| # | Módulo | Puerto | Básico | Intermedio | Avanzado |
|---|--------|--------|--------|------------|----------|
| 1 | [`m01-primer-chat`](m01-primer-chat) | 8080 | `ChatClient` y primer endpoint | Modelo y temperatura por propiedades | Tokens y costo por llamada |
| 2 | [`m02-chatclient`](m02-chatclient) | 8082 | System prompt y `PromptTemplate` | Streaming SSE y opciones por request | Metadata y uso de tokens |
| 3 | [`m03-salidas-estructuradas`](m03-salidas-estructuradas) | 8083 | Respuesta a record con enums | Listas y Bean Validation | Reintento con `StructuredOutputValidationAdvisor` |
| 4 | [`m04-memoria-advisors`](m04-memoria-advisors) | 8084 | Session memory en RAM | Session memory JDBC | Advisor propio de PII y compactación con resumen |
| 5 | [`m05-rag`](m05-rag) | 8085 | ETL y búsqueda por similitud | PDF, `QuestionAnswerAdvisor` y filtros | RAG modular con reescritura de consulta |
| 6 | [`m06-tool-calling`](m06-tool-calling) | 8086 | `@Tool` simple | Tools con JDBC y API REST | Confirmación humana, propiedad y anti-replay |
| 7a | [`m07-mcp-server`](m07-mcp-server) | 8087 | Tools MCP | Resources y prompts | Token y validación de `Origin` |
| 7b | [`m07-mcp-client-agentes`](m07-mcp-client-agentes) | 8088 | Cliente MCP | Encadenamiento y enrutamiento | Orquestador-trabajadores y evaluador-optimizador |
| 8 | [`m08-multimodal`](m08-multimodal) | 8089 | Visión | Voz a texto, texto a voz e imágenes | Reclamo con foto y voz, resistente a manipulación |
| 9 | [`m09-produccion`](m09-produccion) | 8090 | Métricas y Prometheus | Moderación, caché y evaluación | Presupuesto por usuario, resiliencia y evals con dato esperado |

## Preparación
```bash
# 1. Clave de OpenAI en ~/.openai.env (ver el módulo 1)
source ~/.openai.env

# 2. Compilar y ejecutar todas las pruebas (no necesitan API key; el módulo 5 necesita Docker)
mvn test

# 3. Ejecutar un módulo
mvn -pl m02-chatclient spring-boot:run
```
El módulo 7 necesita además `export ACME_MCP_TOKEN=$(openssl rand -hex 24)`, el mismo valor en el servidor y en el cliente.

## Modelos usados
| Uso | Modelo |
|---|---|
| Chat | `gpt-4o-mini` |
| Visión para reclamos (m08) | `gpt-4o` |
| Embeddings (m05) | `text-embedding-3-small` |
| Voz a texto / texto a voz (m08) | `gpt-4o-mini-transcribe` / `gpt-4o-mini-tts` |
| Imágenes (m08) | `gpt-image-1-mini` |

Los modelos se retiran: `gpt-image-1` ya no estaba disponible al probar el módulo 8. Lista los vigentes con
`curl https://api.openai.com/v1/models -H "Authorization: Bearer $OPENAI_API_KEY"`.
Fija un límite mensual de gasto en el panel de OpenAI.

## Hilo conductor: lo que solo se descubre con el modelo real
Todas las pruebas automáticas usan modelos simulados. Al ejecutar cada módulo con OpenAI aparecieron fallas que
esas pruebas no detectan. Cada README documenta las suyas, y son la parte más valiosa del curso:

| Módulo | Falla encontrada | Lección |
|---|---|---|
| m03 | `.entity(List<X>)` fallaba | OpenAI exige un objeto en la raíz: envuelve las listas en un record |
| m04 | `curl -d` mandaba el correo como `ana%40acme.com` y el enmascarado no lo veía | Restringe el tipo de contenido que aceptas |
| m06 | El modelo intentó cancelar el pedido de otro cliente por una inyección | El system prompt no protege; la validación en código sí |
| m07 | El evaluador rechazaba respuestas de 47 palabras "por pasar de 60" | Lo medible se verifica en código; el modelo solo juzga lo subjetivo |
| m08 | Una nota de voz falsa hacía que una pantalla rota se clasificara como defecto de fábrica | Separa la evidencia de lo que dice el usuario |
| m08 | `gpt-4o-mini` no veía un defecto de 6 píxeles | Elige el modelo por tarea |
| m09 | Los dos jueces aprobaron una respuesta incorrecta | Las evaluaciones necesitan un dato esperado verificado en código |

## Notas de Spring AI 2.x
- `ChatClient.options()` recibe el builder de opciones, no el objeto construido.
- `ChatMemory` y `MessageChatMemoryAdvisor` están deprecados (se eliminan en 3.0); el curso usa session memory.
- `QuestionAnswerAdvisor` está en el artefacto `spring-ai-vector-store-advisor`.
- La confirmación humana de tools (`@Tool(requiresConfirmation = true)`) existe desde 2.1.0.
- El transporte SSE de MCP está deprecado; usa Streamable HTTP.
- En Boot 4, `AutoConfigureMockMvc` está en `org.springframework.boot.webmvc.test.autoconfigure` y el starter web es `spring-boot-starter-webmvc`.
