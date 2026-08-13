# FileStation API

The backend for **FileStation** — a Spring Boot REST API for secure file sharing plus a set of free, no-account-required PDF tools: compress, convert (Word/PPTX/Excel/HTML/PNG/JPG ⇄ PDF), and DOCX translation via a self-hosted LLM.

## Features

- **File sharing** — upload, list, download, delete, and toggle public visibility on files; publicly-shared files are viewable via a share link without login.
- **PDF tools** — run without an account; only downloading the finished result requires signing in:
  - Compress PDF (re-encodes embedded images at a chosen quality).
  - Convert PDF → Word / PNG / JPG / HTML, and Word / PPTX / Excel / HTML / PNG / JPG → PDF (via headless LibreOffice and PDFBox).
  - Translate a DOCX file between languages, rebuilding the document in place (via a self-hosted Ollama model) while preserving formatting, tables, and images.
  - Job results are ephemeral: stored as temp files, streamed back once, and deleted immediately after download (or after 1 hour if never downloaded).
- **Auth** — email/password and Google sign-in, JWT access + refresh tokens, multi-session management (list/revoke other sessions).
- **Payments** — Stripe-backed credit purchases and transaction history.

## Tech stack

- Java 17, Spring Boot 3.5 (Web, Security, Data MongoDB, Validation)
- MongoDB
- JJWT for JWT auth, Stripe SDK for payments
- Apache PDFBox (PDF/image processing) and Apache POI (DOCX processing)
- Headless LibreOffice (`soffice`) for Office format conversions
- A self-hosted Ollama instance for DOCX translation
- springdoc-openapi for Swagger UI

## Prerequisites

- Java 17+ and Maven (or use the bundled `./mvnw`)
- MongoDB instance (local or Atlas)
- Docker, if you want to run LibreOffice/Ollama via `docker-compose.yml` instead of installing them natively

## Configuration

Copy `.env.example` to `.env` and fill in the values:

| Variable | Purpose |
|---|---|
| `SPRING_DATA_MONGODB_URI` | MongoDB connection string |
| `JWT_SECRET_KEY` | Secret used to sign JWTs |
| `STRIPE_API_KEY` / `STRIPE_WEBHOOK_SECRET` | Stripe credit purchases |
| `APP_FRONTEND_URL` | Frontend origin (used in emails/redirects) |
| `GOOGLE_CLIENT_ID` | Google OAuth sign-in |
| `OLLAMA_API_URL` / `OLLAMA_MODEL` / `OLLAMA_NUM_THREAD` / `OLLAMA_NUM_CTX` | Self-hosted Ollama instance used for DOCX translation |
| `LIBREOFFICE_PATH` | Path to the `soffice` binary (leave unset on Docker/Linux where it's on `PATH`) |
| `PORT` | Server port (default `8080`) |

## Running locally

```bash
./mvnw spring-boot:run
```

The API is served under the `/api/v1.0` context path, e.g. `http://localhost:8080/api/v1.0`.

## Running with Docker

`docker-compose.yml` starts the app alongside an Ollama sidecar (auto-pulls the translation model on first start). LibreOffice is baked into the app image.

```bash
docker compose up --build
```

## API overview

All routes are prefixed with `/api/v1.0`. Endpoints under `/auth`, `/webhooks`, `/files/public/**`, `POST /files/upload`, `POST /pdf/{compress,translate,from-pdf,to-pdf}`, and `GET /pdf/jobs/*` work without authentication; everything else requires a `Bearer` JWT.

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/{login,register,google}`, `GET /auth/refresh` |
| Users | `GET/PUT /users/me`, `PUT /users/me/password`, `DELETE /users/me`, `GET /users/credits` |
| Sessions | `GET /users/sessions`, `DELETE /users/sessions/{id}`, `DELETE /users/sessions/others` |
| Files | `POST /files/upload`, `GET /files/my`, `GET /files/public/{id}`, `GET /files/download/{id}`, `PATCH /files/{id}/toggle-public`, `DELETE /files/{id}` |
| PDF jobs | `POST /pdf/compress`, `POST /pdf/translate`, `POST /pdf/from-pdf`, `POST /pdf/to-pdf`, `GET /pdf/jobs`, `GET /pdf/jobs/{id}`, `GET /pdf/jobs/{id}/download` |
| Payments | `POST /payments/create-order`, `GET /transactions`, `POST /webhooks/stripe` |

Full request/response schemas are available via Swagger UI at `/api/v1.0/swagger-ui.html` once the app is running.

## Project structure

```
src/main/java/in/phamvu/cloudshareapi/
├── config/       # Security, CORS, and bean configuration
├── controller/   # REST endpoints
├── service/      # Business logic (PDF jobs, translation, auth, payments, ...)
├── repository/   # Spring Data MongoDB repositories
├── document/     # MongoDB document models
├── dto/          # Request/response payloads
└── security/     # JWT filter and auth principal
```
