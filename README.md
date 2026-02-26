# WealthTech Search API

A search API for a WealthTech platform that allows advisors to search across clients and their documents. Client search uses substring matching on name, email, and description. Document search uses semantic similarity via sentence embeddings (all-MiniLM-L6-v2) combined with keyword matching.

## Tech Stack

- Java 21, Spring Boot 4.0.3
- H2 in-memory database
- ONNX Runtime for local embedding inference (all-MiniLM-L6-v2, 384-dimensional vectors)
- DJL HuggingFace Tokenizer

## Setup

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker (for containerized runs)

### Run locally

```bash
mvn clean package -DskipTests
java -jar target/search-api.jar
```

The ONNX model and tokenizer are downloaded automatically from HuggingFace on first startup (~80MB). Subsequent starts use the cached files in `models/`.

### Run with Docker

```bash
docker build -t search-api .
docker run -p 8080:8080 search-api
```

Or using Docker Compose:

```bash
docker compose up --build
```

### Run tests

```bash
mvn test
```

### Access points

| URL | Description                                                                            |
|---|----------------------------------------------------------------------------------------|
| http://localhost:8080/swagger-ui.html | Swagger UI                                                                             |
| http://localhost:8080/h2-console | H2 database console (JDBC URL: `jdbc:h2:mem:searchdb`, user: `searchapi`, no password) |

## API Endpoints

### POST /clients

Create a new client.

```bash
curl -X POST http://localhost:8080/clients \
  -H "Content-Type: application/json" \
  -d '{
    "first_name": "David",
    "last_name": "Baz",
    "email": "david.baz@outlook.com",
    "description": "Senior financial advisor specializing in wealth management",
    "social_links": ["https://linkedin.com/in/johndoe"]
  }'
```

Response (201):
```json
{
  "id": "f22d15dc-21ff-4c51-8b21-3709f663df7f",
  "first_name": "David",
  "last_name": "Baz",
  "email": "david.baz@outlook.com",
  "description": "Senior financial advisor specializing in wealth management",
  "social_links": ["https://linkedin.com/in/johndoe"]
}
```

### POST /clients/{id}/documents

Add a document to a client.

```bash
curl -X POST http://localhost:8080/clients/{id}/documents \
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
  "client_id": "6175a391-bb71-43ac-be9d-8e73f30360fd",
  "title": "Utility Bill",
  "content": "This document contains a utility bill from the electric company. It serves as proof of residential address for the client.",
  "created_at": "2026-02-24T21:23:17.088806875"
}
```

### GET /search?q={query}

Search across clients and documents. Returns a flat array of results, each with a `type` field (`"client"` or `"document"`).

**Client matching:** case-insensitive substring match on first name, last name, email, and description.

**Document matching:** semantic similarity using cosine similarity against chunk embeddings (threshold: 0.4), combined with keyword matching on title and content.

## Example Search Queries

### Find a client by partial email

```bash
curl http://localhost:8080/search?q=outlook
```

```json
[
  {
    "type": "client",
    "id": "f22d15dc-21ff-4c51-8b21-3709f663df7f",
    "first_name": "John",
    "last_name": "Doe",
    "email": "john.doe@outlook.com",
    "description": "Senior financial advisor specializing in wealth management",
    "social_links": ["https://linkedin.com/in/johndoe"]
  }
]
```

### Semantic search — "address proof" finds utility bill

```bash
curl http://localhost:8080/search?q=address%20proof
```

```json
[
  {
    "type": "document",
    "id": "8c12b92a-1a8b-424b-9f04-61ba495dc60c",
    "client_id": "6175a391-bb71-43ac-be9d-8e73f30360fd",
    "title": "Utility Bill",
    "content": "This document contains a utility bill from the electric company. It serves as proof of residential address for the client.",
    "created_at": "2026-02-24T21:23:17.088807"
  }
]
```

### No results

```bash
curl http://localhost:8080/search?q=xyznonexistent
```

```json
[]
```

## Error Handling

| Code | Scenario |
|---|---|
| 400 | Missing/blank required fields, malformed JSON, invalid UUID |
| 404 | Client not found |
| 409 | Duplicate email or duplicate document title for a client |

Example validation error:
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

- **Local embeddings (ONNX Runtime):** The all-MiniLM-L6-v2 model runs locally with no external API calls. This keeps the search self-contained and avoids API rate limits or costs. The model is downloaded once on first startup.
- **Chunking strategy:** Documents are split into ~150-word chunks with 25-word overlap. Chunks smaller than 50 words are merged into the previous chunk. This ensures each chunk has enough context for meaningful embeddings.
- **Similarity threshold (0.4):** Empirically tuned to balance recall and precision for this use case. Broad enough to catch semantic matches like "address proof" matching "utility bill" (score: ~0.41), while filtering noise.
- **Keyword + semantic search:** Document search combines both approaches. Keyword matching ensures exact substring matches are always returned. Semantic matching catches conceptual similarities.
- **H2 in-memory database:** Initial build out to use H2. Later changes will consist of supporting PostgreSQL. 