# Notas del curso

El índice de módulos y la preparación están en el [README](README.md).

## Lo que solo se descubre con el modelo real
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
