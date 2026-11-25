# Módulo 4: Memoria (session memory) y Advisors

> Spring AI 2.1 reemplaza `ChatMemory` y `MessageChatMemoryAdvisor` (deprecados, se eliminan en 3.0) por **session memory**.
> Este módulo usa la API nueva. Si mantienes código 1.x, verás la API anterior en la [guía de migración](https://docs.spring.io/spring-ai/reference/api/session.html).

## Teoría
- El modelo no recuerda nada entre llamadas. La "memoria" consiste en reenviar los mensajes anteriores en cada request.
- **Session**: la identidad de una conversación (id, expiración, metadatos como el dueño).
- **SessionEvent**: cada mensaje se guarda como un evento en un **log de solo agregar**. Nunca se edita ni se borra.
- **Turno**: un mensaje del usuario más todo lo que sigue hasta el siguiente (respuestas, llamadas a tools y sus resultados).
  Las ventanas y compactaciones cortan siempre en límites de turno, así el modelo nunca ve una llamada a tool sin su resultado.
- **Vista activa**: la parte del log que se envía al modelo. La **compactación** no borra mensajes: agrega un límite que
  define la vista desde ese momento. Un `CompactionTrigger` decide cuándo (`TurnCountTrigger`, `TokenCountTrigger`) y una
  `CompactionStrategy` decide qué queda (`WindowCompactionStrategy`: últimos N turnos, opcionalmente con resumen hecho por el modelo).
- **SessionService** con dos repositorios: `InMemorySessionRepository` (pruebas y demos) y `JdbcSessionRepository`
  (PostgreSQL, MySQL, MariaDB, SQL Server, H2). El starter `spring-ai-starter-model-session-jdbc` lo autoconfigura.
- **SessionAdvisors** conecta la sesión con el `ChatClient`. El id de sesión es obligatorio en cada llamada
  (`.advisors(SessionAdvisors.session(id))`): no hay sesión por defecto, porque mezclaría usuarios.
- Más mensajes = más tokens = más costo y latencia. Compacta.
- **Advisor**: interceptor de la llamada al modelo. Implementa `BaseAdvisor` con `before(request)` y `after(response)`.
  `getOrder()` define el orden: menor valor se ejecuta primero. Los advisors de sesión van en `HIGHEST_PRECEDENCE + 200`
  (turno) y `+ 1000` (memoria). El `PiiMaskingAdvisor` va en `+ 100` para que la base de datos nunca guarde el dato original.
- **Cuidado con la codificación**: el enmascarado opera sobre el texto que recibe. Si el cliente envía `form-urlencoded`
  (lo que hace `curl -d` sin cabecera), el correo llega como `ana%40acme.com`, la expresión regular no lo reconoce y el dato
  original se guarda y se envía al modelo. Por eso los endpoints aceptan solo `text/plain`. Lo detectamos probando con el modelo real.
- Casos típicos de advisor: logging, guardrails, enmascarado de datos, RAG (módulo 5), caché, métricas.

## Endpoints (puerto 8084)
| Endpoint | Nivel | Qué muestra |
|---|---|---|
| `POST /memory/{id}` | Básico | Sesión en memoria, ventana de 5 turnos al pasar de 10 |
| `POST /persistent/{id}` | Intermedio | Sesión JDBC (H2 en archivo `./data`), sobrevive a reinicios |
| `POST /safe/{id}` | Avanzado | Sesión JDBC + `PiiMaskingAdvisor` + compactación con resumen |
| `GET /sessions/{id}/view` | Avanzado | Lo que el modelo verá en el próximo turno |
| `DELETE /sessions/{id}` | Intermedio | Borrar una sesión |

```bash
source ~/.openai.env
mvn -pl m04-memoria-advisors spring-boot:run
curl -X POST -H 'Content-Type: text/plain' localhost:8084/memory/ana -d "Me llamo Ana"
curl -X POST -H 'Content-Type: text/plain' localhost:8084/memory/ana -d "¿Cómo me llamo?"
curl -X POST -H 'Content-Type: text/plain' localhost:8084/persistent/ana -d "Mi pedido es el 4521"
# reinicia la app y pregunta de nuevo:
curl -X POST -H 'Content-Type: text/plain' localhost:8084/persistent/ana -d "¿Cuál era mi pedido?"
# envía 7 mensajes a /safe/luis y revisa la vista: verás el resumen y los últimos turnos
curl localhost:8084/sessions/luis/view
```

## Ejercicios
**Básico**
1. Baja el `TurnCountTrigger` del cliente en memoria a 2 y comprueba que el asistente olvida el inicio de la conversación.
2. Usa dos `id` distintos y verifica que no comparten contexto.
3. Reinicia la app y compara `/memory` con `/persistent`.

**Intermedio**
4. Cambia H2 por PostgreSQL con Docker (`spring.datasource.*` y driver). El dialecto se detecta solo; activa `initialize-schema=always`.
5. Guarda el dueño al crear la sesión (`SessionAttributes.PRINCIPAL_NAME`) y rechaza con 404 a quien pida una sesión ajena.
6. Lista las sesiones de un usuario con `sessionService.findByMetadata(SessionAttributes.PRINCIPAL_NAME, "ana")`.

**Avanzado**
7. Crea un `GuardrailAdvisor` que rechace mensajes con palabras prohibidas sin llamar al modelo (revisa `SafeGuardAdvisor` como referencia).
8. Escribe un advisor que mida el tiempo de cada llamada y lo publique como métrica Micrometer.
9. Pon `getOrder()` del `PiiMaskingAdvisor` en `HIGHEST_PRECEDENCE + 2000` y demuestra con una prueba que el dato original queda guardado.
10. Cambia `TurnCountTrigger` por `TokenCountTrigger` y compara el costo de una conversación larga con y sin resumen.
11. Activa `asyncCompaction(true)` y mide la latencia del turno que dispara la compactación, antes y después.
