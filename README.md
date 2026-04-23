# Dialect

> *Speak plain English. Get SQL back.*

![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-6DB33F?logo=springboot&logoColor=white)
![Spring AI](https://img.shields.io/badge/Spring_AI-2.0-6DB33F)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue)

Dialect connects to your PostgreSQL database, reads the live schema, and lets you query it in plain English. Ask a question, get SQL and real rows back.

```bash
curl -X POST http://localhost:8080/api/queries \
  -H "Content-Type: application/json" \
  -d '{"question": "What are the top 5 products by revenue?"}'
```

```json
{
  "success": true,
  "data": {
    "question": "What are the top 5 products by revenue?",
    "sql": "SELECT p.name, SUM(oi.quantity * oi.unit_price) AS revenue FROM public.order_items oi JOIN public.products p ON p.id = oi.product_id GROUP BY p.id, p.name ORDER BY revenue DESC LIMIT 5",
    "explanation": "Joins order_items with products, sums quantity × unit_price per product, returns the top 5 by revenue.",
    "dialect": "postgresql",
    "rows": [
      { "name": "Product A", "revenue": 12345.67 },
      { "name": "Product B", "revenue": 9876.54 }
    ],
    "rowCount": 5,
    "executionTimeMs": 42
  },
  "timestamp": "2026-04-23T08:00:00Z"
}
```

---

## How it works

```
POST /api/queries  { "question": "..." }
        │
        ▼
  QueryController
        │
        ├── SchemaAgent          loads and caches live schema from PostgreSQL
        ├── SchemaRetrievalAgent narrows schema to tables relevant to the question
        ├── PromptAgent          assembles the LLM prompt with schema + rules
        ├── LlmAgent             calls Ollama, parses structured SQL response
        ├── ValidationAgent      blocks non-SELECT, forbidden keywords, bad references
        └── ExecutionAgent       runs the query, returns rows + timing
```

Only `SELECT` queries reach the database. Everything else is rejected before execution.

---

## Agents

### `SchemaAgent`
Introspects PostgreSQL on startup and caches a `SchemaCatalog` containing tables, columns, types, nullability, defaults, and foreign-key relationships.

### `SchemaRetrievalAgent`
Reduces the schema context sent to the model on each request. It tokenises the question, scores tables by name and column relevance, selects seed tables, then expands through foreign-key paths to keep the subset joinable. This keeps prompts small and accurate on large databases.

### `PromptAgent`
Assembles the final prompt — reduced schema slice, the user question, SQL rules, and output shape constraints — from `src/main/resources/prompts/sql-generation.st`.

### `LlmAgent`
Calls the configured chat model via Spring AI, cleans common malformed JSON wrappers, and parses the response into a typed `SqlQueryResult`. Rejects blank or unparseable output.

### `ValidationAgent`
Enforces safety before any SQL touches the database:
- single statement only
- must start with `SELECT`
- blocks `DROP`, `DELETE`, `INSERT`, `UPDATE`, `TRUNCATE`, `ALTER`, and others
- validates alias and column references against the introspected schema

### `ExecutionAgent`
Runs validated SQL through `JdbcOperations` and returns rows, row count, and execution time.

---

## Getting started

### Prerequisites

- Java 24
- Docker and Docker Compose
- [Ollama](https://ollama.com) running locally

```bash
# Pull a model — codellama gives the best SQL accuracy
ollama pull codellama

# Or use llama3.2 for a lighter footprint
ollama pull llama3.2
```

### Run with Docker Compose

```bash
docker compose up --build
```

This starts the application on `http://localhost:8080` and PostgreSQL on `localhost:5432`. Ollama is expected to be running on the host machine.

### Run with Gradle

```bash
./gradlew bootRun
```

Requires a local PostgreSQL instance at `localhost:5432/dialect`.

### Reset demo data

```bash
docker compose down -v
docker compose up --build
```

---

## Configuration

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/dialect` | Database URL |
| `SPRING_DATASOURCE_USERNAME` | `dialect` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | — | Database password |
| `SPRING_AI_OLLAMA_BASE_URL` | `http://host.docker.internal:11434` | Ollama endpoint |
| `SPRING_AI_OLLAMA_CHAT_OPTIONS_MODEL` | `llama3.2` | Model name |

> **Docker note:** The app uses `host.docker.internal` to reach Ollama on the host machine. If Ollama is not reachable, start it with `OLLAMA_HOST=0.0.0.0 ollama serve`.

---

## API

### `POST /api/queries`

**Request**
```json
{ "question": "How many customers signed up last month?" }
```

**Success response** `200 OK`
```json
{
  "success": true,
  "data": {
    "question": "How many customers signed up last month?",
    "sql": "SELECT COUNT(*) AS new_customers FROM public.users WHERE created_at >= DATE_TRUNC('month', NOW() - INTERVAL '1 month') AND created_at < DATE_TRUNC('month', NOW())",
    "explanation": "Counts users created within the previous calendar month.",
    "dialect": "postgresql",
    "rows": [{ "new_customers": 142 }],
    "rowCount": 1,
    "executionTimeMs": 18
  },
  "timestamp": "2026-04-23T08:00:00Z"
}
```

**Error response**
```json
{
  "success": false,
  "error": {
    "code": "SQL_VALIDATION_FAILED",
    "message": "Query contains forbidden keyword: DROP"
  },
  "timestamp": "2026-04-23T08:00:00Z"
}
```

**Error codes**

| Code | Status | Cause |
|---|---|---|
| `INVALID_REQUEST` | 400 | Missing or invalid request body |
| `MALFORMED_JSON` | 400 | Unparseable request JSON |
| `SQL_VALIDATION_FAILED` | 422 | Generated SQL failed safety checks |
| `QUERY_EXECUTION_FAILED` | 502 | SQL was valid but the database returned an error |
| `SCHEMA_LOAD_FAILED` | 500 | Could not introspect the database schema |
| `INTERNAL_SERVER_ERROR` | 500 | Unexpected failure |

---

## Model recommendations

| Model | Command | Notes |
|---|---|---|
| `codellama` | `ollama pull codellama` | Best SQL accuracy, recommended |
| `deepseek-coder` | `ollama pull deepseek-coder` | Excellent on complex joins |
| `llama3.2` | `ollama pull llama3.2` | Lighter footprint, good for simple queries |
| `mistral` | `ollama pull mistral` | Fast, reliable for straightforward questions |

---

## Project structure

```
src/main/kotlin/dev/gavin/dialect/
├── agent/
│   ├── ExecutionAgent.kt
│   ├── LlmAgent.kt
│   ├── PromptAgent.kt
│   ├── SchemaAgent.kt
│   ├── SchemaRetrievalAgent.kt
│   └── ValidationAgent.kt
├── config/
│   ├── DialectConfig.kt
│   ├── SampleDataInitializer.kt
│   └── SqlScriptRunner.kt
├── controller/
│   └── QueryController.kt
├── exception/
│   ├── GlobalExceptionHandler.kt
│   ├── QueryExecutionException.kt
│   ├── SchemaLoadException.kt
│   └── SqlValidationException.kt
└── model/
    ├── ApiResponse.kt
    ├── ErrorResponse.kt
    ├── QueryRequest.kt
    ├── QueryResponse.kt
    └── SqlQueryResult.kt
```

---

## Roadmap

**Near-term**
- Config-driven blocked schemas, tables, and columns
- Result redaction for sensitive fields
- Table and column descriptions in schema metadata for better retrieval
- Column-level retrieval in `SchemaRetrievalAgent`, not just table-level
- SQL AST parsing for stronger validation beyond regex
- Query cost controls and row-count execution limits

**Future ideas**
- Conversation memory via Spring AI `ChatMemory` — refine queries across turns ("now filter that by region")
- Vector store few-shot — embed past successful question→SQL pairs and retrieve the most relevant examples per request
- Query history — persist each question, generated SQL, and result metadata for audit and reuse
- Explain mode — run `EXPLAIN ANALYZE` on generated SQL and return the execution plan
- Export endpoint — stream results as CSV or JSON

---

## Contributing

PRs are welcome. Please open an issue first for large changes so we can discuss the approach.

---

## License

MIT