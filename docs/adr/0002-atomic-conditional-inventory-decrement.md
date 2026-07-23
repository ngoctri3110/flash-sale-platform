# Use an atomic conditional update for production inventory decrement

The production order flow will decrement available inventory with one PostgreSQL update conditioned on sufficient quantity and will treat an affected-row count of zero as insufficient stock. Optimistic and pessimistic locking remain concurrency-lab implementations for learning and measurement; the atomic update is selected because the invariant is simple, the lock duration is short, and application-level conflict retries are unnecessary.

## Consequences

Correctness must be proven with PostgreSQL integration and concurrency tests, and the repository adapter must expose the affected-row outcome explicitly. Benchmarks will record correctness, throughput, latency, retry rate, and lock wait for all three strategies.
