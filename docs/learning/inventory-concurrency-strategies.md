# Inventory concurrency strategy lab

All experiments use the same invariant: accepted quantity never exceeds
`available_quantity`; a rejected attempt does not mutate inventory. Run the
existing Concurrency Lab workload at low and high contention for each strategy
and record throughput, p95 latency, retries and PostgreSQL lock waits. Results
are machine observations, not universal thresholds.

| Strategy | Correctness behavior | Cost | Production decision |
| --- | --- | --- | --- |
| Atomic conditional decrement | `UPDATE ... SET available_quantity = available_quantity - :q WHERE available_quantity >= :q` accepts exactly available stock | one short write statement; rejected writes are normal | **Used by Order flow** |
| Pessimistic lock | `SELECT ... FOR UPDATE`, read, then write serializes contenders | lock wait grows with transaction duration; deadlock/order discipline needed | Teaching/reference only |
| Optimistic lock | read version, update `WHERE version = :v`, retry conflicts | no blocking read, but high contention causes retry storms and tail latency | Teaching/reference only |

## What to observe

At low contention all three preserve the invariant. At high contention, a
naive read-modify-write loses updates; pessimistic locking trades throughput for
waiting, while optimistic locking trades it for application retries. Atomic SQL
keeps the critical section in PostgreSQL and returns an explicit failure when
stock is insufficient, so it is the simplest safe MVP default.

Do not replace the production implementation with global JPA locking merely to
match the lab: the Order path deliberately uses the atomic conditional update
from ADR 0002.
