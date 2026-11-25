# Módulo 5: Embeddings, Vector Store y RAG

## Teoría
- **Embedding**: vector numérico que representa el significado de un texto. Textos parecidos quedan cerca (similitud coseno).
  Modelo usado: `text-embedding-3-small` (1536 dimensiones). La dimensión de la tabla debe coincidir con la del modelo.
- **Vector Store**: base de datos que guarda embeddings y busca los más cercanos. Aquí PGVector (PostgreSQL), con índice HNSW.
- **RAG** (Retrieval Augmented Generation): en vez de confiar en la memoria del modelo, buscas fragmentos relevantes de tus
  documentos y los pones en el prompt. Reduce alucinaciones y permite usar datos privados o recientes.
- **ETL de documentos**: *Extract* (`TextReader`, `PagePdfDocumentReader`), *Transform* (`TokenTextSplitter` en chunks) y *Load* (`vectorStore.add`).
  El tamaño del chunk es una decisión de calidad: muy grande diluye el significado, muy pequeño pierde contexto.
- **Metadatos y filtros**: guarda `category`, `source`, etc. y filtra en la búsqueda (`category == 'envios'`). Sirve para permisos, idioma o versión de documento.
- **`similarityThreshold`**: descarta resultados poco parecidos. Sin umbral siempre devuelves algo, aunque no tenga relación.
- **RAG modular** (`RetrievalAugmentationAdvisor`): etapas intercambiables. Reescritura de la consulta (`RewriteQueryTransformer`),
  recuperación (`VectorStoreDocumentRetriever`), postproceso de documentos (`DocumentPostProcessor`, aquí se haría el reranking) y
  aumento del prompt (`ContextualQueryAugmenter`). Con `allowEmptyContext(false)` el asistente no inventa cuando no hay contexto.

## Ejecutar
Requiere Docker. Al iniciar la app, Spring Boot levanta PGVector con `compose.yaml` (puerto 5433).
Esto necesita el plugin `docker compose` (o el binario `docker-compose`). Si tu Docker no lo tiene
(por ejemplo, algunas instalaciones con Lima o Colima), levanta el contenedor a mano y desactiva la integración:
```bash
docker run -d --name m05-pgvector -e POSTGRES_DB=acme -e POSTGRES_USER=acme -e POSTGRES_PASSWORD=acme \
  -p 5433:5432 pgvector/pgvector:pg17
mvn -pl m05-rag spring-boot:run -Dspring-boot.run.arguments=--spring.docker.compose.enabled=false
```
```bash
source ~/.openai.env
mvn -pl m05-rag spring-boot:run
curl -X POST localhost:8085/ingest
curl "localhost:8085/search?q=cuanto%20dura%20la%20garantia"
curl "localhost:8085/ask?q=Cuanto%20cuesta%20el%20envio%20express"
curl "localhost:8085/ask?q=Puedo%20devolver%20un%20producto&category=devoluciones"
curl "localhost:8085/ask-modular?q=y%20si%20no%20estoy%20en%20casa"
curl -X POST localhost:8085/ingest/pdf -F file=@manual.pdf -F category=manual
```

| Endpoint | Nivel | Qué muestra |
|---|---|---|
| `POST /ingest`, `GET /search` | Básico | ETL de Markdown y búsqueda por similitud sin LLM |
| `POST /ingest/pdf`, `GET /ask` | Intermedio | ETL de PDF, `QuestionAnswerAdvisor`, filtro por metadatos |
| `GET /ask-modular` | Avanzado | RAG modular con reescritura de consulta y umbral |

Pruebas (no necesitan API key, usan Testcontainers y embeddings falsos): `mvn -pl m05-rag test`.

## Ejercicios
**Básico**
1. Ejecuta `/search` con preguntas con y sin palabras exactas del documento. Revisa el `score` de cada resultado.
2. Cambia `rag.chunk-size` a 50 y a 800, vuelve a ingerir y compara los resultados de `/search`.
3. Agrega un cuarto documento `pagos.md` y verifica que `/ask` lo use.

**Intermedio**
4. Sube un PDF real (manual de un producto). Revisa cómo quedan los chunks y ajusta el tamaño.
5. Agrega la metadata `language` y filtra con una expresión compuesta: `category == 'envios' && language == 'es'`.
6. Pregunta algo que no está en los documentos. Observa la respuesta con `/ask` (umbral bajo) y súbelo hasta que el asistente admita que no sabe.

**Avanzado**
7. Implementa un `DocumentPostProcessor` de reranking (por ejemplo, pedirle al modelo que puntúe de 0 a 10 cada fragmento) y conéctalo con `documentPostProcessors(...)`.
8. Agrega `MultiQueryExpander` para generar 3 variantes de la pregunta y compara resultados contra la versión sin expansión.
9. Escribe un conjunto de 10 preguntas con respuesta esperada y mide qué porcentaje recupera el documento correcto en el top 3. Cambia chunk size, umbral y `topK` y compara.
10. Combina RAG con la memoria del módulo 4 para que "¿y el exprés?" se entienda con el contexto de la pregunta anterior (`CompressionQueryTransformer`).
