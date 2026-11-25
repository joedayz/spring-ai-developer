# Módulo 7b: Cliente MCP y patrones de agentes

## Teoría: cliente MCP
- El starter `spring-ai-starter-mcp-client` se conecta a los servidores configurados en
  `spring.ai.mcp.client.streamable-http.connections.<nombre>.url`, descubre sus tools y las publica como un
  `ToolCallbackProvider`. Para el `ChatClient` son tools normales (módulo 6).
- **Credenciales**: un `McpSyncHttpClientRequestCustomizer` agrega la cabecera `Authorization`. Se aplica a todas las
  conexiones, así que `McpClientConfig` solo la envía al host y puerto de Acme: si agregas otro servidor, no le filtras el token.
- **Sesión compartida**: con el protocolo actual, el cliente MCP autoconfigurado es uno solo para toda la app.
  No lo uses como frontera de usuario o de tenant.
- Las tools de un servidor de terceros son código ajeno: sus descripciones y resultados llegan al modelo y pueden
  contener *prompt injection*. Conecta solo servidores en los que confíes y exige confirmación (módulo 6) para tools con efectos,
  por ejemplo con `spring.ai.tools.confirmation.tools=<nombre>`.

## Teoría: patrones de agentes
Basados en *Building effective agents* (Anthropic). Son flujos con pasos definidos en código: más predecibles,
baratos y fáciles de probar que un agente autónomo. Empieza por el más simple que resuelva el problema.

| Patrón | Endpoint | Cuándo usarlo |
|---|---|---|
| Encadenamiento | `POST /agents/chain` | La tarea se divide en pasos fijos y cada uno usa la salida del anterior |
| Enrutamiento | `POST /agents/route` | Entradas de distintos tipos que necesitan un tratamiento especializado |
| Orquestador-trabajadores | `POST /agents/orchestrate` | No sabes de antemano cuántas subtareas habrá; se ejecutan en paralelo |
| Evaluador-optimizador | `POST /agents/refine` | Hay criterios claros de calidad y la iteración mejora el resultado |

### Lecciones de probar con el modelo real (`gpt-4o-mini`)
El evaluador-optimizador falló varias veces antes de funcionar. Cada falla es una lección:
1. **El generador inventaba datos** (`[número de teléfono]`) porque no conocía los canales de Acme. Dale los hechos.
2. **El generador no conocía los criterios**: al corregir uno rompía otro (pasó de 50 a 82 palabras). Generador y evaluador deben compartir reglas.
3. **El modelo cuenta mal**: rechazaba respuestas de 47 palabras "por pasar de 60", incluso cuando se le pedía no evaluar la longitud.
4. **El evaluador era inconsistente**: decía "falta un siguiente paso" en respuestas que lo tenían.

La solución: **lo verificable se verifica en código** (longitud, canal válido, placeholders, en `checkInCode`) y el modelo
solo juzga lo subjetivo (tono, promesas) con una **rúbrica de un booleano por criterio** y temperatura 0.
Después de estos cambios aprobó en el primer intento en todas las pruebas.

Otros controles: el orquestador limita las subtareas a 4 aunque el modelo pida más, y `maxIterations` se acota entre 1 y 5.
Sin límites, un modelo puede disparar el costo.

## Ejecutar
```bash
source ~/.openai.env
export ACME_MCP_TOKEN=<el mismo token del servidor>
mvn -pl m07-mcp-server spring-boot:run            # terminal 1
mvn -pl m07-mcp-client-agentes spring-boot:run    # terminal 2, puerto 8088

curl localhost:8088/assistant/tools
curl -X POST -H 'Content-Type: text/plain' localhost:8088/assistant -d "¿Qué monitores tienen y cuál tiene más stock?"
curl -X POST -H 'Content-Type: text/plain' localhost:8088/agents/route -d "Me cobraron dos veces"
curl -X POST -H 'Content-Type: text/plain' localhost:8088/agents/refine -d "Un cliente está molesto porque su pedido 4521 lleva 8 días de retraso. Respóndele."
```
Pruebas sin API key ni servidor: `mvn -pl m07-mcp-client-agentes test`.

## Ejercicios
**Básico**
1. Desde el asistente, pregunta por un producto que no existe y revisa en el log del servidor qué tool se llamó.
2. Agrega un cuarto especialista al enrutador (`RETURNS`, devoluciones).

**Intermedio**
3. Conecta un segundo servidor MCP público (por ejemplo `@modelcontextprotocol/server-filesystem` por STDIO) y revisa los prefijos de nombres de tools.
4. Lee el resource `policy://envios` desde el cliente (`McpSyncClient.readResource`) e inclúyelo como contexto del system prompt.
5. Implementa el patrón de paralelización: evalúa la misma respuesta con 3 revisores en paralelo y aprueba por mayoría.

**Avanzado**
6. Combina enrutamiento y MCP: solo el especialista `SALES` recibe las tools del catálogo.
7. Agrega al evaluador un criterio verificable en código: que mencione el número de pedido si la solicitud lo trae.
8. Mide el costo (tokens) de cada patrón con el `usage` de la respuesta (módulo 2) y compáralos para la misma tarea.
9. Usa un modelo más capaz solo para el evaluador (`OpenAiChatOptions.builder().model(...)`) y compara la tasa de aprobación y el costo.
