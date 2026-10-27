# DocQA: Ask Your Documents

A full-stack **Retrieval-Augmented Generation (RAG)** app. Upload PDFs or text files, then ask questions in a chat interface. Every answer is written only from your documents and carries numbered citations that open the exact source passage.

**Stack:** Spring Boot 3 (Java 21) · Angular 18 · Vertex AI (embeddings, Vector Search, Gemini) · JWT auth · PostgreSQL · Docker · GitHub Actions → Google Cloud Run

---

## What it can do

| Area | Capability |
|---|---|
| **Accounts** | Register and sign in with email and password. Passwords are hashed with BCrypt and sessions use signed JWTs. |
| **Documents** | Upload PDF, TXT and Markdown files (up to 20 MB each), several at a time, by click or drag and drop. List and delete them. |
| **Indexing** | Text is extracted page by page, split into overlapping chunks, embedded and stored in a vector index. |
| **Q&A chat** | Ask natural-language questions. The app retrieves the most relevant passages and has an LLM answer from them only. |
| **Citations** | Answers contain markers like `[1]`. Click one to see the source file, page number and the original passage. |
| **Honesty** | If the documents don't contain the answer, the app says so instead of guessing. |
| **Privacy** | Each user only ever searches their own documents. |
| **Offline demo mode** | Runs fully on your laptop with no cloud account, using a simple word-matching engine. |

### How it works

```
                        ┌──────────────────────────── Spring Boot API ────────────────────────────┐
Browser                 │                                                                          │
Angular UI ──/api──▶    │  UPLOAD:  file → extract text (per page) → chunk (≈1000 chars, overlap)  │
(served by nginx)       │           → embed (text-embedding-005) → upsert into Vector Search        │
                        │                                                                          │
                        │  ASK:     question → embed → top-k nearest chunks (owner-filtered)       │
                        │           → load chunk text from Postgres → Gemini (grounded prompt)     │
                        │           → answer with [n] citations + source passages                  │
                        └──────────────────────────────┬───────────────────────────────────────────┘
                                                       │
                                  PostgreSQL: users, documents, chunk text
                                  Vertex AI: embeddings, vector index, Gemini
```

---

## Skills and technologies demonstrated

| Category | What is used |
|---|---|
| **Backend** | Java 21, Spring Boot 3, Spring Web, Spring Data JPA/Hibernate, Bean Validation, Lombok, Actuator health checks |
| **Security** | Spring Security 6, stateless JWT (jjwt), BCrypt, CORS, per-user data isolation, upload validation |
| **AI / RAG** | Text chunking with overlap, embeddings (`text-embedding-005`), vector similarity search with metadata filtering, grounded prompting, citation tracking, prompt-injection mitigation |
| **Google Cloud** | Vertex AI Embeddings, Vertex AI Vector Search (streaming updates), Gemini, Cloud Run, Cloud SQL, Artifact Registry, Secret Manager, Workload Identity Federation |
| **Frontend** | Angular 18 standalone components, signals, new control-flow syntax, functional HTTP interceptor and route guard, typed API layer, accessible and responsive CSS with dark mode |
| **Data** | PostgreSQL in production, H2 for local development, JPA entities and indexes |
| **DevOps** | Multi-stage Dockerfiles, Docker Compose, nginx reverse proxy with runtime config, GitHub Actions CI/CD, keyless cloud deploys, infrastructure setup script |
| **Testing** | JUnit 5, Spring Boot Test, MockMvc integration tests for auth, upload, retrieval, citations and cross-user isolation |
| **Design** | Interface-driven provider abstraction (`EmbeddingClient`, `VectorStore`, `LlmClient`) so cloud and offline backends are swappable |

---

## Run it locally

### Option 1: Docker (recommended)

You need [Docker Desktop](https://www.docker.com/products/docker-desktop/) running. No Google account is needed.

```bash
cp .env.example .env            # Windows PowerShell: copy .env.example .env
docker compose up --build
```

Open **http://localhost:4200**, create an account, add a document, and ask a question.
The first build takes several minutes. Stop with `Ctrl+C`, then `docker compose down`.

### Option 2: Without Docker

You need JDK 21, Maven 3.9+ and Node 20+. Use two terminals.

```bash
# Terminal 1: API on http://localhost:8080 (uses a local H2 database file)
cd backend
mvn spring-boot:run

# Terminal 2: UI on http://localhost:4200 (proxies /api to port 8080)
cd frontend
npm install
npm start
```

### Option 3: Use real Vertex AI locally

1. Create a Google Cloud project with billing enabled and run `./infra/setup-gcp.sh` (see [Deploy](#deploy-to-google-cloud)). It creates the vector index and prints the IDs you need.
2. Run `gcloud auth application-default login`.
3. In `.env`, set `RAG_PROVIDER=vertex` and fill in the `GCP_*` and `VERTEX_*` values and `GOOGLE_ADC_FILE`.
4. Run `docker compose up --build`.

### Two modes

| | `local` (default) | `vertex` |
|---|---|---|
| Embeddings | Hashed word counts | `text-embedding-005` |
| Vector search | In-memory | Vertex AI Vector Search |
| Answers | Quotes best-matching sentences | Gemini, grounded and cited |
| Understands meaning | No, matches shared words | Yes |
| Needs Google Cloud | No | Yes |

In local mode, phrase questions with words that appear in the document.

### Try it with a sample

Upload `sample-handbook.txt` and ask:

- *How many days of annual leave do employees get?*
- *What is the home office allowance?*
- *How long must passwords be?*
- *What is the dress code?* (not in the file, so the app should say it found nothing)

### Run the tests

```bash
cd backend && mvn verify
```

---

## Configuration

Set these as environment variables (`.env` for Docker Compose).

| Variable | Default | Purpose |
|---|---|---|
| `RAG_PROVIDER` | `local` | `local` or `vertex` |
| `JWT_SECRET` | dev value | Signing key, at least 32 characters. **Change it in production.** |
| `JWT_EXPIRATION_MINUTES` | `480` | Token lifetime |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | local H2 file | Database connection |
| `RAG_CHUNK_SIZE` / `RAG_CHUNK_OVERLAP` | `1000` / `150` | Chunking, in characters |
| `RAG_TOP_K` | `5` | Passages retrieved per question |
| `GCP_PROJECT_ID`, `GCP_LOCATION` | (empty), `us-central1` | Google Cloud project and region |
| `EMBEDDING_MODEL`, `GENERATION_MODEL` | `text-embedding-005`, `gemini-2.5-flash` | Vertex AI models |
| `VERTEX_INDEX_ID`, `VERTEX_INDEX_ENDPOINT_ID`, `VERTEX_DEPLOYED_INDEX_ID`, `VERTEX_PUBLIC_ENDPOINT_DOMAIN` | (empty) | Vector Search identifiers |
| `CORS_ORIGINS` | `http://localhost:4200` | Allowed browser origins when not using the nginx proxy |

---

## API

All routes except `/api/auth/**` need the header `Authorization: Bearer <token>`.

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register` | `{email, password}` → `{token, email}` |
| POST | `/api/auth/login` | `{email, password}` → `{token, email}` |
| GET | `/api/documents` | List your documents |
| POST | `/api/documents` | Upload (`multipart/form-data`, field `file`) |
| DELETE | `/api/documents/{id}` | Delete a document and its vectors |
| POST | `/api/query` | `{question}` → `{answer, sources[{n, filename, page, snippet, score}]}` |
| GET | `/actuator/health` | Health check |

Errors always return `{"message": "..."}` with a suitable status code.

---

## Project structure

```
docqa/
├── backend/                     Spring Boot API
│   └── src/main/java/com/docqa/
│       ├── config/              Security, CORS, typed properties
│       ├── security/            JWT service and request filter
│       ├── domain/ repo/        JPA entities and repositories
│       ├── web/                 Controllers, DTOs, error handler
│       └── rag/                 Chunker, ingestion, query, provider interfaces
│           ├── local/           Offline embedder, in-memory index, extractive answerer
│           └── vertex/          Vertex AI embeddings, Vector Search, Gemini (REST)
├── frontend/                    Angular 18 app + nginx config
├── infra/setup-gcp.sh           One-time Google Cloud setup
├── .github/workflows/           ci.yml and deploy.yml
└── docker-compose.yml           Local stack: Postgres + API + UI
```

---

## Deploy to Google Cloud

1. Run `PROJECT_ID=<id> GITHUB_REPO=<you>/docqa ./infra/setup-gcp.sh` (Cloud Shell works well). It enables the APIs, creates Artifact Registry, Cloud SQL, secrets, service accounts, keyless GitHub authentication and the vector index. The index deployment takes 30 to 60 minutes.
2. Add the secrets and variables the script prints under **GitHub → Settings → Secrets and variables → Actions**.
3. Push to `main`. `deploy.yml` tests, builds both images, pushes them, deploys `docqa-backend` and `docqa-frontend` to Cloud Run and smoke-tests the result. Pull requests run `ci.yml`.

**Cost note:** Cloud SQL and the Vector Search endpoint bill while they exist. Delete them (or the whole project) when you finish demoing. Free hosting tiers exist elsewhere but are tight for a Java service: check each provider's current memory limits, sleep behaviour and database expiry first.

---

## Design decisions

- **Tenant isolation in two layers.** Each vector carries an `owner` filter, and chunk text is re-read from the database with `WHERE owner_id = ?`. A mistake in one layer cannot leak another user's text.
- **Grounded answers.** The prompt restricts Gemini to the retrieved passages, requires `[n]` citations, tells it to say "not found" rather than guess, and treats document text as data, not instructions. Temperature is low (0.2).
- **Page-aware chunking.** Chunks are built per PDF page, preferring sentence then word boundaries, so citations can say "page 7".
- **Asymmetric embeddings.** `RETRIEVAL_DOCUMENT` for passages and `RETRIEVAL_QUERY` for questions, with vectors normalised so dot product equals cosine similarity.
- **Swappable providers.** The RAG pipeline depends on three small interfaces, which keeps tests and demos independent of the cloud.
- **Same-origin API.** nginx proxies `/api` to the backend, so the browser needs no CORS setup in deployment.

---

## Known limitations

- Ingestion is **synchronous**: large files make the upload request wait.
- **Scanned (image-only) PDFs** have no text to extract; OCR is not included.
- The schema is managed by Hibernate `ddl-auto=update`. Use migrations (Flyway) for production.
- No rate limiting, email verification or password reset.
- Answers are **not streamed**; the full reply appears when ready.
- Vector Search upserts can take a few seconds to become searchable.
- Chat history is kept in the browser tab only and is lost on refresh.

---

## Ideas for more features

**Retrieval quality**
- Hybrid search (keyword + vector) and a re-ranking step
- Query rewriting that uses earlier chat turns for follow-up questions
- Filter or scope a question to selected documents
- Evaluation set with retrieval and answer-quality metrics

**Product**
- Streaming answers (Server-Sent Events) with a typing effect
- Saved conversations and chat history per user
- Support for DOCX, PPTX, HTML, CSV and web URLs
- OCR for scanned PDFs (Document AI or Tesseract)
- Highlight the cited passage inside an in-app document viewer
- Thumbs up/down feedback on answers
- Shareable workspaces and team folders

**Platform**
- Background ingestion with Cloud Tasks or Pub/Sub and live progress
- Flyway migrations, Redis caching, and rate limiting
- Refresh tokens, OAuth/Google sign-in, email verification, roles
- Observability: structured logs, OpenTelemetry traces, token and cost dashboards
- Terraform for the full infrastructure
- Frontend unit tests and Playwright end-to-end tests

---

## Résumé summary

- Built a RAG-based document Q&A app (Spring Boot, Angular, Vertex AI embeddings and vector search) with JWT auth and chat-style answers citing source passages.
- Containerized with Docker and deployed on GCP with GitHub Actions CI/CD.
