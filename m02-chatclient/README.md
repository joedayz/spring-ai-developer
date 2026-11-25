# Módulo 2: ChatClient y prompting

## Teoría
- **System prompt por defecto**: `builder.defaultSystem(...)` fija el comportamiento del asistente. Se carga desde `prompts/system.st`.
- **PromptTemplate**: plantillas con variables `{nombre}`. Separa texto de datos y evita concatenar strings.
- **Streaming**: `.stream().content()` devuelve `Flux<String>`. Con SSE el usuario ve la respuesta mientras se genera.
- **Opciones por request**: `.options(OpenAiChatOptions.builder()...)` sobrescribe las del modelo en una llamada. En Spring AI 2.x `options()` recibe el *builder*, no el objeto construido.
- **Advisors**: interceptores de la llamada. `SimpleLoggerAdvisor` registra request y response (módulo 4 profundiza).
- **Metadata**: `chatResponse()` expone modelo y uso de tokens.

## Endpoints
| Endpoint | Nivel | Qué muestra |
|---|---|---|
| `/ask?question=` | Básico | System prompt por defecto |
| `/ticket?producto=&problema=` | Básico | `PromptTemplate` |
| `/stream?question=` | Intermedio | SSE |
| `/creative?question=&temperature=` | Intermedio | Opciones por request |
| `/usage?question=` | Avanzado | Metadata y tokens |

```bash
source ~/.openai.env
mvn -pl m02-chatclient spring-boot:run
curl "localhost:8082/ask?question=Mi%20impresora%20no%20enciende"
curl -N "localhost:8082/stream?question=Cuentame%20un%20chiste"
```

## Ejercicios
**Básico**
1. Edita `system.st` para que el asistente responda con formato de lista. Verifica el cambio.
2. Crea una plantilla para resumir un texto en N palabras, con variables `texto` y `n`.

**Intermedio**
3. Consume `/stream` desde una página HTML con `EventSource`.
4. Agrega un parámetro `maxTokens` a `/creative` y observa cómo se corta la respuesta.

**Avanzado**
5. Mueve las plantillas a `src/main/resources/prompts/` con un archivo por caso de uso y escribe pruebas que verifiquen que cada variable se sustituye (sin llamar al modelo).
6. Crea un `ChatClient` por rol (soporte y ventas) con distinto system prompt y selecciónalo con `@Qualifier`.
7. Calcula el costo acumulado por request usando `/usage` y guárdalo en una métrica Micrometer.
