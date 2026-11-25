# Módulo 9: Producción

Un asistente de preguntas frecuentes (`POST /faq`) con los controles que necesita antes de exponerse a usuarios reales.
Cada control es un advisor y se aplica en este orden: **moderación (50) → caché (60) → presupuesto (70) → modelo**.

## Teoría

### Observabilidad
- Spring AI publica métricas y trazas con Micrometer sin código extra: `gen_ai_client_token_usage` (tokens por modelo y tipo),
  `gen_ai_client_operation_seconds` (latencia del modelo) y `spring_ai_advisor_seconds` (tiempo de cada advisor).
- Este módulo agrega métricas de negocio: `acme_ai_cache_total{result}`, `acme_ai_moderation_blocked_total`,
  `acme_ai_budget_rejected_total` y `acme_ai_tokens_total`. Se exponen en `/actuator/prometheus`.
- **Nunca actives** `spring.ai.chat.observations.log-prompt` ni `log-completion` en producción: escriben los mensajes de los usuarios en los logs.

### Seguridad y abuso
- **Moderación** (`ModerationAdvisor`): el modelo de moderación de OpenAI revisa la entrada antes del chat. Si la marca, se responde un
  mensaje fijo y el modelo de chat nunca la ve.
- **Límite de entrada**: 500 caracteres. Limita el costo y la superficie de *prompt injection*.
- **Prompt injection**: el system prompt separa los hechos con `<hechos>` y declara que el texto del usuario es una pregunta, no instrucciones.
  Ayuda, pero no es una garantía: los controles que realmente protegen están en código (propiedad de recursos y confirmación en el módulo 6,
  decisiones en código en los módulos 7 y 8).

### Costos
- **Caché** (`ResponseCacheAdvisor`): normaliza la pregunta (mayúsculas, tildes, espacios, signos) y responde sin llamar al modelo.
  La clave incluye el system prompt, así que un cambio de instrucciones invalida las respuestas viejas.
  Solo para respuestas que no dependen del usuario: en un chat con datos personales, una caché compartida filtra datos entre usuarios.
- **Presupuesto por usuario** (`TokenBudgetAdvisor`): tokens por usuario y por día. Al agotarse responde 429.
  En memoria solo sirve con una instancia; con varias usa Redis o la base de datos.
- `max-completion-tokens=300` limita el largo (y el costo) de cada respuesta.

### Resiliencia
- El cliente de OpenAI reintenta errores transitorios (`spring.ai.openai.max-retries`) y corta con `spring.ai.openai.timeout`.
- `AiErrorHandler` traduce los errores a respuestas claras sin detalles internos: 429/5xx de OpenAI → **503 con `Retry-After`**,
  401/403 (error de configuración nuestro) → 500, sin respuesta → 504.

### Evaluación
- `SupportEvaluationTest` corre contra el modelo real y **no se ejecuta en `mvn test` normal** porque cuesta dinero:
  ```bash
  source ~/.openai.env
  mvn -pl m09-produccion test -Devals=true -Dtest=SupportEvaluationTest
  ```
- Combina dos capas: un **dato esperado verificado en código** y dos **jueces** (`RelevancyEvaluator` y `FactCheckingEvaluator`).
  Ejecútala antes de cambiar el prompt o el modelo: es tu prueba de regresión de calidad.

## Lecciones de probar con el modelo real
1. **Los jueces aprobaron una respuesta incorrecta.** A "¿garantía de los audífonos?" el asistente respondió *3 meses* (son 12) y los dos
   evaluadores la dieron por buena: un juez detecta respuestas que no suenan respaldadas, no respuestas equivocadas que suenan bien.
   La causa era que los hechos no decían si los audífonos son electrónicos o accesorios. Se corrigieron los hechos y se agregó el dato esperado.
2. **El juez no es determinista.** La misma respuesta ("No lo sé, contacta a soporte@acme.test") salió *no respaldada* en una ejecución y
   *respaldada* en otra. No bases una decisión en una sola corrida del juez.
3. **La moderación tiene un costo en latencia.** Las métricas mostraron unos 0,6 s por pregunta, también en los aciertos de caché.
4. **Las métricas propias deben cuadrar con las de Spring AI.** `acme_ai_tokens_total` (2334) coincidió con `gen_ai_client_token_usage_total`.
5. **La caché necesita normalizar tildes**: "envio expres" no encontraba "envío exprés".

## Ejecutar
```bash
source ~/.openai.env
mvn -pl m09-produccion spring-boot:run          # puerto 8090
curl -X POST -H 'X-User: ana' -H 'Content-Type: text/plain' localhost:8090/faq -d "¿Cuánto cuesta el envío exprés?"
curl -s localhost:8090/actuator/prometheus | grep -E "^(gen_ai|acme_ai)"
```
Pruebas sin API key: `mvn -pl m09-produccion test`.

## Ejercicios
**Básico**
1. Arma un panel en Grafana (o revisa `/actuator/metrics/gen_ai.client.token.usage`) con tokens por minuto y tasa de aciertos de la caché.
2. Calcula el costo en dólares con los precios vigentes de OpenAI a partir de `gen_ai_client_token_usage_total{gen_ai_token_type}`.
3. Agrega 5 preguntas al conjunto de evaluación, incluida una que hoy falle, y corrige los hechos hasta que pase.

**Intermedio**
4. Ejecuta la moderación en paralelo con la llamada al chat (`CompletableFuture`) y descarta la respuesta si la entrada se marca. Mide la latencia antes y después.
5. Mueve el presupuesto a Redis con expiración diaria y pruébalo con Testcontainers.
6. Reemplaza la caché LRU por Caffeine o Spring Cache y agrega la métrica de tamaño.

**Avanzado**
7. Implementa una caché semántica: guarda el embedding de cada pregunta (módulo 5) y reutiliza la respuesta si la similitud supera 0,95. Mide falsos aciertos con el conjunto de evaluación.
8. Agrega un *circuit breaker* (Resilience4j) para dejar de llamar a OpenAI durante un minuto tras varios 5xx seguidos, y responde con un mensaje fijo.
9. Corre la evaluación tres veces y reporta la tasa de aprobación de cada pregunta en vez de un sí/no.
10. Haz un *canary* de modelo: envía el 10 % del tráfico a otro modelo, etiqueta las métricas y compara costo, latencia y resultados de la evaluación.
