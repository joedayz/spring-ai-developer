# Módulo 1: Fundamentos y primer chat

## Teoría
- **LLM**: modelo que predice el siguiente token. No "sabe" hechos: genera texto probable.
- **Token**: fragmento de texto (aprox. 3/4 de palabra). Se cobra por token de entrada y de salida.
- **Prompt**: instrucciones y contexto que envías. Roles: `system` (reglas), `user`, `assistant`.
- **Temperature**: 0 = más determinista, valores altos = más variación.
- **Alucinación**: respuesta plausible pero falsa. Se mitiga con RAG (módulo 5) y evaluación (módulo 9).
- **Spring AI** da una API portable (`ChatModel`, `ChatClient`) y autoconfiguración por starter.
  Cambiar de proveedor es cambiar de starter y propiedades.

### Piezas del proyecto
- Starter `spring-ai-starter-model-openai`: crea el `ChatModel` y un `ChatClient.Builder`.
- `ChatClient`: API fluida (`prompt().user(..).call().content()`), parecida a `RestClient`.
- Propiedades: `spring.ai.openai.api-key`, `spring.ai.openai.chat.options.model`.

## Ejecutar
Guarda la clave fuera del repositorio, en un archivo que solo tu usuario pueda leer:
```bash
printf 'export OPENAI_API_KEY=\n' > ~/.openai.env && chmod 600 ~/.openai.env
# edita ~/.openai.env y pega tu "Secret key" (empieza con sk-) después del =
```
En OpenAI crea la clave con permisos restringidos y fija un límite de gasto mensual en Billing.

```bash
source ~/.openai.env
mvn -pl m01-primer-chat spring-boot:run
curl "localhost:8080/chat?message=Hola"
```
Prueba sin API key: `mvn -pl m01-primer-chat test` (usa un `ChatModel` simulado).

## Ejercicios
**Básico**
1. Cambia `temperature` a 0 y a 1.5. Haz la misma pregunta 3 veces y compara.
2. Agrega el parámetro `lang` al endpoint y pide la respuesta en ese idioma.

**Intermedio**
3. Maneja el error cuando la API key es inválida o hay límite de cuota: devuelve 502 con un mensaje claro usando `@RestControllerAdvice`.
4. Cambia el modelo por otro de OpenAI solo con propiedades y compara calidad y latencia.

**Avanzado**
5. Obtén `chatResponse()` y registra tokens de entrada y salida. Calcula el costo con los precios vigentes de OpenAI (configurables en `application.properties`).
6. Habilita `management.endpoints.web.exposure.include=metrics` y explora las métricas `gen_ai.*` disponibles en `/actuator/metrics`.
