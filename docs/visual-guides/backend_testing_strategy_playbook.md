# Playbook 13: Testing strategy cho backend

## Test pyramid có mục đích

| Loại | Bắt lỗi gì | Ví dụ trong repo |
|---|---|---|
| Unit | Pure business branch/policy | Fake port test `PlaceOrder` |
| Integration | SQL, transaction, constraint, mapping, lock | Testcontainers PostgreSQL concurrency/N+1 |
| HTTP/API | Serialization, validation, status/error contract | `OrderApiIntegrationTest` |
| Architecture | Dependency boundary | `ArchitectureRulesTest` |
| Smoke | Compose/Kind wiring | scripts/k8s smoke |

## Rule

Mock không chứng minh PostgreSQL lock/Flyway/query plan/Kafka behavior. Testcontainers chậm hơn nhưng dùng đúng nơi correctness phụ thuộc infrastructure.

## Bài

Với feature booking hold: viết test plan trước code gồm invalid request, success, concurrent last-room, retry idempotency, expiry worker duplicate, migration upgrade. Mỗi test nói invariant/failure nào nó bảo vệ.

## Review checklist

- Assert observable outcome, không assert implementation private.
- Test name nói behavior/rule.
- Concurrency test dùng latch/barrier; không `sleep` để hy vọng race.
- Regression test khi production bug; fixture cô lập và deterministic.

## Interview

“Tôi chọn test level theo failure mode. Unit test nhanh cho policy; với transaction/constraint/concurrency tôi dùng PostgreSQL Testcontainers vì mock repository không chứng minh database semantics.”
