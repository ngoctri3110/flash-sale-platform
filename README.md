# Flash Sale Order & Inventory Platform

A Java Spring Boot + React project to demonstrate senior backend engineering skills:
API design, validation, transactions, race condition handling, PostgreSQL, Kafka, Docker, Kubernetes, performance, clean code, and AI-assisted engineering workflow.

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

Start PostgreSQL:

```powershell
docker compose up -d postgres
```

In one terminal, run the backend with the local profile:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

The health endpoint is available at
`http://localhost:8080/actuator/health`.

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
