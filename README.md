# Flash Sale Order & Inventory Platform

A Java Spring Boot + React project to demonstrate senior backend engineering skills:
API design, validation, transactions, race condition handling, PostgreSQL, Kafka, Docker, Kubernetes, performance, clean code, and AI-assisted engineering workflow.

**Bắt đầu học:** [`Learning Hub`](docs/learning/README.md) — lộ trình theo chặng, bài thực hành và hướng luyện phỏng vấn.

**Mở giao diện học trực tuyến:** [Flash Sale Learning Portal](https://ngoctri3110.github.io/flash-sale-platform/)

## Goal

Build a realistic flash sale system where many users can buy limited-stock products concurrently without overselling.

## AI Workflow

This project uses mattpocock/skills:

- /teach for learning
- /grill-with-docs for requirement clarification
- /to-spec for specification
- /to-tickets for implementation planning
- /tdd for risky business logic
- /implement for coding
- /code-review for review
- /handoff for long-session continuity

Learning hub:

- [`Learning Hub`](docs/learning/README.md) — chọn chặng học, bài thực hành, tiêu chí hoàn thành và lối đi phỏng vấn.
- [`Frontend track`](docs/learning/frontend-track.md) — học Shop/Admin UI qua API contract, idempotency, error state và Concurrency Lab.
- [`Tech Lead system design`](docs/visual-guides/tech_lead_system_design_process_guide.md) — question tree, scope, invariants, diagrams, ADR, delivery, incident và phỏng vấn.
- [`Redis trong Spring Boot`](docs/visual-guides/redis_spring_boot_beginner_guide.md) — cache, rate limit, lock, session, Lua, annotations, custom patterns và failure flows.

Các chặng lớn:

```text
0 Orientation
→ 1 Spring request/data
→ 2 Clean Architecture + API
→ 3 Transaction/concurrency/correctness
→ 4 Data/performance/failure
→ 5 Security/operations
→ 6 Microservices/distributed systems
→ 7 AI-native delivery + interview
```

## Current foundation

- Java 21 and Spring Boot backend
- React and TypeScript frontend
- PostgreSQL for local development and Testcontainers for integration tests
- OpenAPI 3.1 contract for Product, Inventory, and Order
- ArchUnit rules protecting clean architecture dependency direction

## Prerequisites

- Java 21
- Docker Desktop
- Node.js 24 or newer

The Maven Wrapper is included, so a separate Maven installation is optional.

## Run locally

Start the complete local demo:

```powershell
docker compose up --build
```

The Shop/Admin frontend is available at `http://localhost:5173`; backend health is
available at `http://localhost:8080/actuator/health/readiness`.

For development without containers, run the backend with the local profile:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

The health endpoint is available at
`http://localhost:8080/actuator/health`.

After the Compose stack is ready, run the API smoke test:

```powershell
.\scripts\smoke-local.ps1
```

In another terminal, run the frontend:

```powershell
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/actuator` to the local backend, so
the page can display its real health state without a development-only CORS
configuration.

Stop local infrastructure with:

```powershell
docker compose down
```

The named PostgreSQL volume is preserved between runs.

## Verify

Backend integration and architecture tests require a running Docker daemon:

```powershell
cd backend
.\mvnw.cmd test
```

Frontend tests, type checking, and production build:

```powershell
cd frontend
npm ci
npm test
npm run typecheck
npm run build
```

Lint the API contract from the project root:

```powershell
npx --yes @redocly/cli lint openapi/openapi.yaml
```

## API contract

The design-first contract is in [`openapi/openapi.yaml`](openapi/openapi.yaml).
Authentication and real payment processing are explicitly out of scope for the
first phase.
