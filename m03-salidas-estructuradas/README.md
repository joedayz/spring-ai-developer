# Módulo 3: Salidas estructuradas

## Teoría
- Un LLM devuelve texto. Para usarlo en código necesitas un objeto tipado: Spring AI agrega al prompt
  las instrucciones de formato (esquema JSON derivado de tu clase) y convierte la respuesta con `.entity(...)`.
- `.entity(TicketAnalysis.class)` para un objeto. Para colecciones, envuelve la lista en un record (`ProductMentions`): OpenAI exige que la raíz del JSON sea un objeto, y con `List<X>` directo el modelo real devolvió un objeto y falló la deserialización (el modelo simulado de las pruebas no lo detecta).
- `@JsonPropertyDescription` en los campos del record guía al modelo (qué significa cada campo). Los enums limitan los valores posibles.
- El modelo puede devolver JSON inválido o con valores fuera de rango. Nunca confíes en la salida:
  valida con Bean Validation (`@NotBlank`, `@Size`...) antes de usarla.
- `StructuredOutputValidationAdvisor` valida contra el esquema y reintenta la llamada (`maxRepeatAttempts`) cuando falla.
- Usa `temperature=0` para extracción de datos: menos variación.

## Endpoints (puerto 8083)
| Endpoint | Nivel | Qué muestra |
|---|---|---|
| `POST /classify` | Básico | Respuesta mapeada a un record con enums |
| `POST /products` | Intermedio | Lista de objetos + Bean Validation (422 si es inválido) |
| `POST /classify-robust` | Avanzado | Reintento automático ante JSON inválido |

```bash
source ~/.openai.env
mvn -pl m03-salidas-estructuradas spring-boot:run
curl -X POST -H 'Content-Type: text/plain' localhost:8083/classify -d "Me cobraron dos veces la suscripción y necesito el reembolso hoy"
```

## Ejercicios
**Básico**
1. Agrega un campo `language` (enum `ES`, `EN`, `PT`) a `TicketAnalysis` y verifica que el modelo lo llene.
2. Quita `@JsonPropertyDescription` de `priority` y observa si cambia la calidad de las respuestas.

**Intermedio**
3. Crea un endpoint que extraiga de una factura en texto: proveedor, fecha (`LocalDate`) e importes (`BigDecimal`). Agrega validaciones (`@PastOrPresent`, `@Positive`).
4. Devuelve el detalle de las violaciones en JSON (`ProblemDetail`) en vez de un mensaje de texto.

**Avanzado**
5. Escribe una prueba que simule una respuesta con prefijo de texto antes del JSON ("Aquí está: {...}") y revisa cómo reacciona el parser.
6. Aumenta `maxRepeatAttempts`, simula siempre JSON inválido y define qué debe responder tu API cuando se agotan los reintentos.
7. Mide cuántos tokens extra consume el esquema en el prompt (módulo 2, `/usage`) con un record pequeño y uno de 20 campos.
