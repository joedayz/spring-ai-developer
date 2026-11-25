# spring-ai-developer
Curso para Youtube de JoeDayzPeru.

Curso de Spring AI de inicio a avanzado, construido sobre un mismo proyecto: el asistente de soporte de Acme,
una tienda ficticia de electrónica. Cada módulo agrega una capacidad nueva y trae su `README.md` con teoría,
cómo ejecutarlo y ejercicios en tres niveles: básico, intermedio y avanzado.

**Stack:** Java 25, Spring Boot 4.2.0, Spring AI 2.1.0 y OpenAI. El módulo 5 usa PGVector con Docker.
**Prerrequisito:** conocer Spring Boot.

## Módulos

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

1. Crea una clave en [platform.openai.com](https://platform.openai.com/api-keys), con permisos restringidos,
   y fija un límite de gasto mensual en Billing.
2. Guárdala fuera del repositorio, en un archivo que solo tu usuario pueda leer:
   ```bash
   printf 'export OPENAI_API_KEY=\n' > ~/.openai.env && chmod 600 ~/.openai.env
   # edita ~/.openai.env y pega la clave (empieza con sk-) después del =
   ```
3. Compila y ejecuta todas las pruebas. No necesitan API key; el módulo 5 necesita Docker.
   ```bash
   mvn test
   ```
4. Ejecuta un módulo:
   ```bash
   source ~/.openai.env
   mvn -pl m02-chatclient spring-boot:run
   ```

El módulo 7 necesita además `export ACME_MCP_TOKEN=$(openssl rand -hex 24)`, con el mismo valor en el servidor y en el cliente.

## Modelos usados

| Uso | Modelo |
|---|---|
| Chat | `gpt-4o-mini` |
| Visión para reclamos (m08) | `gpt-4o` |
| Embeddings (m05) | `text-embedding-3-small` |
| Voz a texto / texto a voz (m08) | `gpt-4o-mini-transcribe` / `gpt-4o-mini-tts` |
| Imágenes (m08) | `gpt-image-1-mini` |

OpenAI retira modelos con el tiempo: `gpt-image-1` ya no estaba disponible al probar el módulo 8.
Lista los vigentes con `curl https://api.openai.com/v1/models -H "Authorization: Bearer $OPENAI_API_KEY"`.

## Lo que solo se descubre con el modelo real

Las pruebas automáticas usan modelos simulados. Al ejecutar cada módulo con OpenAI aparecieron fallas que esas
pruebas no detectan; están en [`COURSE.md`](COURSE.md) junto con las notas de migración a Spring AI 2.x.
