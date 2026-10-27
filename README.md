# DocQA: RAG document Q&A

Upload documents, ask questions, get chat-style answers that cite the exact source passages.

**Stack:** Spring Boot 3 (Java 21) · Angular 18 · Vertex AI (text-embedding-005, Vector Search, Gemini) · JWT auth · Docker · GitHub Actions → Cloud Run

```
Angular (nginx) ──/api──▶ Spring Boot ──▶ Postgres (users, documents, chunk text)
                              │
                              ├─ ingest: PDF/TXT/MD → per-page text → overlapping chunks → embeddings → Vector Search (owner-restricted)
                              └─ query:  question → embedding → top-k neighbours → chunk text → Gemini (grounded, cites [n]) → answer + sources
```

## Run locally (no Google account needed)

```bash
cp .env.example .env
docker compose up --build        # open http://localhost:4200
```
Default `RAG_PROVIDER=local` uses an offline hashing embedder, an in-memory vector index and an extractive "answerer", which is
enough to test the full flow. Without Docker: run the backend (`cd backend && mvn spring-boot:run`, H2 file DB)
and the UI (`cd frontend && npm install && npm start`, proxied to :8080).

## Use real Vertex AI

1. `PROJECT_ID=<id> GITHUB_REPO=<you>/docqa ./infra/setup-gcp.sh` creates the APIs, Artifact Registry, Cloud SQL, secrets,
   service accounts, GitHub keyless auth (Workload Identity Federation) and the Vector Search index and endpoint.
   The index deploy takes 30-60 minutes. The script prints every value you need.
2. Locally: `gcloud auth application-default login`, set `RAG_PROVIDER=vertex` plus the `VERTEX_*` and `GCP_PROJECT_ID`
   values and `GOOGLE_ADC_FILE` in `.env`, then `docker compose up --build`.

## Deploy (CI/CD)

Add the secrets and variables the script prints to GitHub, then push to `main`.
`.github/workflows/deploy.yml` runs tests → builds both images → pushes to Artifact Registry → deploys
`docqa-backend` and `docqa-frontend` to Cloud Run → smoke-tests `/actuator/health`. PRs and other branches run `ci.yml`.

## API

| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | `{email, password}` → `{token, email}` |
| GET | `/api/documents` | your documents |
| POST | `/api/documents` | multipart `file` (.pdf/.txt/.md, 20 MB) |
| DELETE | `/api/documents/{id}` | removes text and vectors |
| POST | `/api/query` | `{question}` → `{answer, sources[{n, filename, page, snippet, score}]}` |

All except `/api/auth/**` need `Authorization: Bearer <jwt>`.

## Design notes (good interview talking points)

- **Tenant isolation, twice:** every vector carries an `owner` restrict filter, and chunk text is re-fetched with `WHERE owner_id = ?`, so an index mistake can't leak another user's text.
- **Grounding:** the Gemini prompt allows only the supplied passages, requires `[n]` citations, says "not found" instead of guessing and treats passage text as data (prompt-injection defence). Temperature 0.2.
- **Chunking:** ~1000 chars with 150 overlap, split at sentence then word boundaries, per PDF page so citations carry page numbers.
- **Asymmetric embeddings:** `RETRIEVAL_DOCUMENT` for passages, `RETRIEVAL_QUERY` for questions; vectors are L2-normalised so dot product equals cosine.
- **Provider abstraction:** `EmbeddingClient`, `VectorStore` and `LlmClient` interfaces with local and Vertex implementations, so tests and demos run offline.
- **Known limits:** ingestion is synchronous (fine for ≤20 MB; move to Cloud Tasks/Pub/Sub for scale), scanned PDFs need OCR, schema uses Hibernate `ddl-auto=update` (switch to Flyway for production), and no rate limiting yet.
