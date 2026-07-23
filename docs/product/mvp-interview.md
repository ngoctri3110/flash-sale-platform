# MVP Interview Record

This record captures the shared understanding reached through `/grill-with-docs`. It is input for `/to-spec`, not a replacement for the eventual specification.

## Outcome

Build a local, end-to-end Flash Sale Order & Inventory Platform that demonstrates senior backend engineering through API contracts, validation, transaction boundaries, concurrency control, persistence behavior, Kafka reliability, query performance, and evidence-based review of AI-generated code.

## Technical baseline

- Java 21 LTS, Spring Boot 4.1.x, Maven Wrapper, and PostgreSQL 18.
- Spring Data JPA, Flyway, Bean Validation, Spring Kafka, Actuator, JUnit, AssertJ, and Testcontainers.
- React, TypeScript, and Vite for the frontend.
- One modular monolith organized package-by-feature with lightweight Clean Architecture.
- No Lombok initially; use explicit Java and records where they improve clarity.

## MVP domain and behavior

- Domain concepts are Product, Inventory, Order, Customer, and Inventory Adjustment as defined in `CONTEXT.md`.
- Each Product has exactly one Inventory and no SKU, variant, reservation, or warehouse model.
- Each Order purchases one Product with quantity from 1 to 5.
- Only accepted Orders are persisted. Insufficient inventory returns `409 INSUFFICIENT_STOCK` without an Order or inventory side effect.
- A client-supplied Customer UUID simulates identity; it is not authentication.
- There is no FlashSale entity, campaign, schedule, original/discount price, or voucher. Limited inventory and concurrent demand provide the flash-sale scenario.
- Product price is snapshotted into the accepted Order.

## API

- Design `openapi.yaml` before controller implementation; it is the primary HTTP contract.
- Do not encode roles in resource URLs. Operations intended for administrators remain documented as such until authorization is added.
- Product: list, detail, create, and partial update.
- Inventory: list and create a reasoned quantity adjustment for a Product.
- Order: create and list.
- Local/demo data is seeded, while APIs still support product creation and inventory management.
- List APIs use zero-based offset pagination, default size 20, maximum size 100, allowlisted sort fields, and `id` as a stable tie-breaker.
- Production list responses use stable DTOs/projections rather than serialized JPA entities or `PageImpl`.

### Order idempotency

- `POST /api/v1/orders` requires `Idempotency-Key` of 8–128 characters.
- The key is unique per Customer.
- Repeating the same key and payload returns the existing Order without another inventory deduction.
- Reusing the key with a different payload returns `409 IDEMPOTENCY_KEY_REUSED`.

### Error contract

- Use RFC 9457 `application/problem+json` through Spring `ProblemDetail`/`ErrorResponse` support.
- Extensions are `code`, `traceId`, and `fieldErrors` where applicable.
- Validation returns `400 VALIDATION_FAILED`.
- Missing Product returns `404 PRODUCT_NOT_FOUND`.
- Inactive Product returns `409 PRODUCT_NOT_AVAILABLE`.
- Insufficient inventory returns `409 INSUFFICIENT_STOCK`.
- Unexpected failures return `500 INTERNAL_ERROR` without stack traces, SQL, or internal details.

### Validation

- Product name: trimmed, required, 1–120 characters.
- Description: optional, at most 1,000 characters.
- Price: positive `BigDecimal`, at most two fractional digits; MVP currency is VND.
- Initial inventory: 0–1,000,000.
- Order quantity: 1–5; Customer ID must be a UUID.
- Inventory adjustment delta is non-zero, reason is 3–200 characters, and resulting inventory cannot be negative.
- Only an existing active Product may be ordered.

## Transaction and concurrency

- `PlaceOrder` is the application-service transaction boundary.
- Within one PostgreSQL transaction: resolve idempotency, read/snapshot Product price, decrement Inventory conditionally, insert Order, and insert the outbox record.
- Request-shape validation occurs before the transaction; no network call occurs inside it.
- Production uses atomic conditional inventory decrement. Optimistic and pessimistic locking are separate lab implementations evaluated with the same tests and benchmark.
- Acceptance scenario: with inventory 10 and 100 simultaneous one-unit requests, exactly 10 Orders succeed, 90 receive insufficient stock, final inventory is zero, and repeated idempotent requests create no additional Order or deduction.
- Throughput and p95 are comparison measurements, not machine-independent pass/fail gates.

## Database

- Use `BIGINT GENERATED ... AS IDENTITY` for Product, Order, and Inventory Adjustment identifiers.
- Customer and event identifiers are UUIDs; idempotency keys are client strings.
- Tables: `products`, `inventories`, `orders`, `inventory_adjustments`, `outbox_events`, and `processed_events`.
- Inventory uses Product ID as both primary and foreign key and has a non-negative check constraint.
- Orders have a unique constraint on Customer ID plus idempotency key and store price/currency snapshots.
- Outbox and processed-event tables are infrastructure, not additional domain aggregates.

## Kafka

- The order transaction writes `OrderCreated` to the outbox; Kafka is not on the HTTP critical path.
- A polling publisher sends to `order-events.v1`, keyed by Order ID.
- Event envelope includes `eventId`, `eventType`, `eventVersion`, `occurredAt`, and the Order snapshot.
- Delivery is at-least-once. Consumers deduplicate by event ID, retry transient failures with backoff, and route unrecoverable records to a dead-letter topic.

## Frontend and local demo

- Shop screen: view Products, choose quantity, place an Order, and display standardized success/errors.
- Admin screen: create/update Products, adjust/view Inventory, and view paginated Orders.
- Concurrency Lab: configure simultaneous requests and quantity, then show success count, insufficient-stock count, final inventory, and whether overselling occurred.
- Customer ID and idempotency key are generated automatically but can be inspected and replayed.
- The full demo runs locally with Docker Compose. The Vite frontend remains deployable to Vercel, but a public end-to-end backend is outside MVP.

## Learning and verification

- `/tdd` is required for inventory invariants, concurrent ordering, idempotency, rollback, adjustment safety, outbox creation, and consumer idempotency.
- `/teach` is used for Spring transaction proxies/propagation/isolation, concurrency/locking/deadlocks, JPA persistence and N+1, PostgreSQL query plans/indexes, Kafka delivery/outbox, Kubernetes probes/HPA, and AI-code verification.
- The N+1 lab intentionally reproduces the problem and compares fetch join, `@EntityGraph`, batch fetching, and DTO projection. Production list endpoints prefer DTO projection.
- Docker Compose, Kubernetes local, basic metrics/logging, concurrency benchmarks, and the N+1 lab remain in the learning MVP.

## Out of scope

- Real authentication, authorization, accounts, payment, refunds, cancellation, shipping, and real notifications.
- Cart, multi-product Orders, campaigns, schedules, discount pricing, vouchers, variants, reservations, and multiple warehouses.
- Microservices, distributed transactions, Redis, search engines, public cloud deployment, high availability, disaster recovery, and end-to-end exactly-once claims.
