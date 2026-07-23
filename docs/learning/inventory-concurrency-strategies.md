# Inventory concurrency strategy lab

`InventoryConcurrencyStrategyLabIntegrationTest` runs all strategies against
the same PostgreSQL row and invariant: accepted quantity never exceeds
`available_quantity`; a rejected attempt does not mutate inventory.

Run it from `backend`:

```powershell
.\mvnw.cmd -Dtest=InventoryConcurrencyStrategyLabIntegrationTest test
```

The test runs low contention (one unit, one attempt) and high contention (eight
units, 24 simultaneous attempts). It verifies accepted, rejected, and final
quantity for every strategy. It prints throughput, p95 latency, application
retries, and samples of PostgreSQL sessions waiting on locks.

| Strategy | Correctness behavior | Cost | Production decision |
| --- | --- | --- | --- |
| Atomic conditional decrement | `UPDATE ... WHERE available_quantity >= :q` accepts exactly available stock | One short write statement; rejected writes are normal | **Used by Order flow** |
| Pessimistic lock | `SELECT ... FOR UPDATE`, read, then write serializes contenders | Lock waits grow with transaction duration; deadlock/order discipline is needed | Teaching/reference only |
| Optimistic lock | Read version, update `WHERE version = :v`, retry conflicts | No blocking read, but high contention causes retry storms and tail latency | Teaching/reference only |

## Observed run

On the local Docker Desktop/PostgreSQL run on 2026-07-23, high contention
accepted 8 and rejected 16 attempts for every strategy; final inventory was 0.
The lab observed atomic at about 1949 requests/s and 5.90 ms p95, pessimistic at
about 1021 requests/s and 21.35 ms p95 with one lock-wait sample, and optimistic
at about 1096 requests/s and 16.00 ms p95 with 72 retries. These are machine
observations, not universal pass/fail thresholds; rerun them on the target
hardware before making capacity conclusions.

## What the experiment teaches

A naive read-modify-write loses updates under contention. Pessimistic locking
preserves correctness by waiting, but long transaction boundaries increase
blocking and deadlock exposure. Optimistic locking preserves correctness by
detecting conflicts, but shifts the contention cost into retry logic. Atomic SQL
keeps the invariant and decision inside PostgreSQL, avoids application retries,
and treats insufficient stock as an explicit normal outcome.

The `version` column exists to support the lab's optimistic compare-and-swap.
Do not replace the production implementation with global JPA locking merely to
match the lab: the Order path deliberately uses the atomic conditional update
from ADR 0002.
