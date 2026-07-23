# Publish order events through a transactional outbox

Order acceptance and its `OrderCreated` outbox record will commit in the same PostgreSQL transaction; Kafka publishing happens asynchronously through a polling relay. This keeps Kafka off the order request's critical path and chooses at-least-once delivery with idempotent consumers over an unsupported claim of end-to-end exactly-once processing.

## Consequences

The schema includes outbox and processed-event tables. The publisher retries transient failures, unrecoverable consumer records move to a dead-letter topic, and every consumer side effect must tolerate duplicate delivery by `eventId`.
