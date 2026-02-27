# Search API

A search API that allows search across clients and their documents.

Client search uses case-insensitive substring matching on name, email, and description. Document search uses a hybrid approach: semantic similarity via sentence embeddings (all-MiniLM-L6-v2) combined with PostgreSQL full-text keyword matching.

## Tech Stack

- Java 21, Spring Boot 4.0.3
- PostgreSQL 17 + pgvector extension
- ONNX Runtime — local inference with all-MiniLM-L6-v2 (384-dimensional vectors)
- DJL HuggingFace Tokenizer
- Flyway — schema migrations

## Setup

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker + Docker Compose

### Run with Docker Compose (recommended)

```bash
docker compose up --build
```

This starts both PostgreSQL (pgvector/pgvector:pg17) and the API. The ONNX model and tokenizer are downloaded automatically from HuggingFace on first startup (~80MB) and cached in `models/`.

### Run locally (requires a running PostgreSQL instance)

Configure the database connection in `application.properties` or via environment variables, then:

```bash
mvn clean package -DskipTests
java -jar target/search-api.jar
```

### Environment variables

| Variable | Default | Description |
|---|---|---|
| DB_HOST | localhost | PostgreSQL host |
| DB_PORT | 5432 | PostgreSQL port |
| DB_NAME | searchdb | Database name |
| DB_USER | searchapi | Database user |
| DB_PASSWORD | changeme | Database password |

All variables have built-in defaults so `docker compose up --build` works out of the box with no configuration required. The defaults are intentional for local development and assignment review.

For a real deployment, override them by copying `.env.example` to `.env` and setting strong credentials. The `.env` file is gitignored and never committed — only `.env.example` (which contains no real credentials) is tracked in the repository.

### Run tests

Unit tests run without Docker:

```bash
mvn test -pl . -Dtest="ClientControllerTest,DocumentControllerTest,SearchControllerTest,ClientServiceTest,DocumentServiceTest,SearchServiceTest,ChunkingTest"
```

Integration tests require Docker (Testcontainers pulls pgvector/pgvector:pg17 automatically):

```bash
mvn test
```

### Access points

| URL | Description |
|---|---|
| http://localhost:8080/swagger-ui.html | Swagger UI — interactive API docs |

## API Endpoints

### POST /clients

Create a new client.

```bash
curl -X POST http://localhost:8080/clients \
  -H "Content-Type: application/json" \
  -d '{
    "first_name": "Jane",
    "last_name": "Smith",
    "email": "jane.smith@outlook.com",
    "description": "Senior financial advisor specializing in retirement planning",
    "social_links": ["https://linkedin.com/in/janesmith"]
  }'
```

Response (201):
```json
{
  "id": "f22d15dc-21ff-4c51-8b21-3709f663df7f",
  "first_name": "Jane",
  "last_name": "Smith",
  "email": "jane.smith@outlook.com",
  "description": "Senior financial advisor specializing in retirement planning",
  "social_links": ["https://linkedin.com/in/janesmith"]
}
```

### POST /clients/{id}/documents

Add a document to a client. The content is automatically chunked into ~150-word segments and embedded for semantic search.

```bash
curl -X POST http://localhost:8080/clients/f22d15dc-21ff-4c51-8b21-3709f663df7f/documents \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Utility Bill",
    "content": "This document contains a utility bill from the electric company. It serves as proof of residential address for the client."
  }'
```

Response (201):
```json
{
  "id": "8c12b92a-1a8b-424b-9f04-61ba495dc60c",
  "client_id": "f22d15dc-21ff-4c51-8b21-3709f663df7f",
  "title": "Utility Bill",
  "content": "This document contains a utility bill from the electric company. It serves as proof of residential address for the client.",
  "created_at": "2026-02-24T21:23:17.088807"
}
```

### GET /search?q={query}

Search across clients and documents. Returns a flat array of results ordered by relevance. Each result includes a `type` field (`"client"` or `"document"`) to distinguish between the two.

**Client matching:** case-insensitive substring match on first name, last name, email, and description.

**Document matching:** hybrid scoring combining semantic similarity (70%) and keyword relevance (30%). Documents that match via keyword search are always included regardless of semantic score.

## Example Search Queries

### Find a client by partial email domain

```bash
curl "http://localhost:8080/search?q=outlook"
```

```json
[
  {
    "type": "client",
    "id": "f22d15dc-21ff-4c51-8b21-3709f663df7f",
    "first_name": "Jane",
    "last_name": "Smith",
    "email": "jane.smith@outlook.com",
    "description": "Senior financial advisor specializing in retirement planning",
    "social_links": ["https://linkedin.com/in/janesmith"]
  }
]
```

### Semantic search — "address proof" finds utility bill

```bash
curl "http://localhost:8080/search?q=address%20proof"
```

```json
[
  {
    "type": "document",
    "id": "8c12b92a-1a8b-424b-9f04-61ba495dc60c",
    "client_id": "f22d15dc-21ff-4c51-8b21-3709f663df7f",
    "title": "Utility Bill",
    "content": "This document contains a utility bill from the electric company. It serves as proof of residential address for the client.",
    "created_at": "2026-02-24T21:23:17.088807",
    "score": 0.7142857313156128
  }
]
```

### Mixed results — query matches both a client and a document

```bash
curl "http://localhost:8080/search?q=portfolio"
```

```json
[
  {
    "type": "client",
    "id": "a1b2c3d4-0000-0000-0000-000000000001",
    "first_name": "Bob",
    "last_name": "Johnson",
    "email": "bob.johnson@example.com",
    "description": "Portfolio manager and investment specialist"
  },
  {
    "type": "document",
    "id": "8c12b92a-1a8b-424b-9f04-61ba495dc60c",
    "client_id": "a1b2c3d4-0000-0000-0000-000000000001",
    "title": "Investment Portfolio Statement",
    "content": "Quarterly portfolio statement detailing investment performance and asset allocation.",
    "created_at": "2026-02-24T21:23:17.088807",
    "score": 0.8523
  }
]
```

### No results

```bash
curl "http://localhost:8080/search?q=xyznonexistent"
```

```json
[]
```

## Error Handling

| Code | Scenario |
|---|---|
| 400 | Missing or blank required fields, malformed JSON, invalid UUID, missing query parameter |
| 404 | Client not found |
| 409 | Duplicate email or duplicate document title for the same client |

Validation error response:
```json
{
  "status": 400,
  "error": "Bad Request",
  "errors": {
    "first_name": "first_name is required"
  }
}
```

## Architecture Decisions

### Local embeddings via ONNX Runtime
The all-MiniLM-L6-v2 model runs locally using ONNX Runtime. No external API calls are made during inference, which means no rate limits, no per-request cost, and no network dependency at search time. The model (~80MB) is downloaded once from HuggingFace on first startup and cached in `models/`.

### PostgreSQL + pgvector
Vector embeddings are stored directly in PostgreSQL using the pgvector extension rather than a separate vector database. This keeps the operational footprint minimal: one database handles structured data, full-text search, and vector similarity search in one place.

An in-memory database (e.g. H2) cannot support vector search natively. Without pgvector, every search query would require loading all stored embeddings into the JVM, computing cosine similarity against each one in Java, and sorting the results — an O(n) full scan that grows linearly with the number of document chunks. PostgreSQL's HNSW index replaces this with a sub-linear approximate nearest-neighbour query executed entirely inside the database engine, with no vectors loaded into application memory. 

The chunks table uses an HNSW index (`m=16, ef_construction=64`) for fast approximate nearest-neighbour queries.

### Document chunking
Documents are split into ~150-word chunks with 25-word overlap before embedding. Chunks smaller than 50 words are merged into the preceding chunk. Chunking ensures that large documents produce meaningful per-section embeddings rather than a single averaged vector that dilutes specific topics. Search aggregates the best chunk score per document using `MAX(1 - cosine_distance)`.

### Hybrid document ranking
Document search combines two signals:

```
score = 0.3 × normalisedKeyword + 0.7 × semantic
```

PostgreSQL `ts_rank` keyword scores are normalised to [0, 1] relative to the best match in the result set (with a floor of 0.1 to prevent inflation when all raw scores are very small). Semantic scores come directly from cosine similarity [0, 1].

Documents that match via keyword search (tsvector) are guaranteed to appear in results with a minimum score equal to the threshold — keyword matches are high-precision (no false positives) so they are never filtered out solely because of a low semantic score. A document that matches both semantically and by keyword will always outscore one that matches only one way.

The combined score threshold is **0.25**.

### Client search
Client search uses `ILIKE` substring matching across first name, last name, email, and description. This correctly handles the case where a user searches for part of an email domain (e.g., "outlook" matches "jane.smith@outlook.com"), which full-text search would miss because tsvector treats email addresses as opaque tokens.
