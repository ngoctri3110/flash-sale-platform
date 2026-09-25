# Senior situation workbook

Mỗi scenario làm trong 30–45 phút. Trả lời trong 2 phút, vẽ sequence, chạy test/metric, rồi tự chấm. Không mở đáp án trước.

## Rubric 10 điểm

- 2 điểm: scope và invariant rõ.
- 2 điểm: timeline/concurrency đúng.
- 2 điểm: mechanism phù hợp và source of truth.
- 2 điểm: failure handling + test/metric evidence.
- 2 điểm: trade-off, giới hạn và bước scale tiếp theo.

Đạt nền Senior khi đạt ≥ 8/10 ở cả 6 scenario; dưới 8 thì quay lại lab liên quan.

## Scenario 1 — Oversell hai pod

Stock 10, 100 POST đồng thời, hai backend pod. Hãy chứng minh accepted quantity không vượt 10; phân biệt idempotency key khác nhau với duplicate key.

Evidence: PostgreSQL concurrency test, final stock, tổng quantity accepted, p95, lock wait.

## Scenario 2 — P95 tăng nhưng CPU thấp

API p95 2s, CPU 25%, Hikari pending tăng, PostgreSQL lock wait cao. Chẩn đoán theo thứ tự và nêu thay đổi an toàn đầu tiên.

Evidence: metrics, thread/connection observation, query/lock inspection; không chỉ tăng pod.

## Scenario 3 — Kafka down sau DB commit

Client nhận 201 nhưng audit chưa có, Kafka unavailable 10 phút. Trình bày state từng bảng, retry, alert và khi nào audit xuất hiện.

Evidence: outbox row, publisher attempts/lease, consumer processed event, audit row.

## Scenario 4 — Redis lock owner chết

Worker A giữ lock rồi crash. Worker B phải làm gì? Khi nào TTL an toàn, khi nào lock không đủ để bảo vệ transaction?

Evidence: token compare/delete Lua, TTL, duplicate/lease test, PostgreSQL remains source of truth.

## Scenario 5 — Schema deploy không downtime

App v1 và v2 chạy cùng lúc; v2 cần cột mới bắt buộc. Viết expand/backfill/contract và rollback app/data.

Evidence: two-version compatibility test, Flyway migration, backfill metric, constraint timing.

## Scenario 6 — Catalog timeout trong Order service

Catalog timeout 500ms, Order có 20 worker threads. Thiết kế timeout, retry budget, circuit breaker, bulkhead, error contract và trace.

Evidence: two-service lab, downstream failure test, rejected call metric, no retry storm/duplicate POST.

## Câu trả lời mẫu phải có

```text
Requirement → invariant → timeline → decision
→ failure mode → test/metric evidence → trade-off → next scale step
```

Nếu câu trả lời chỉ có “dùng transaction/Redis/Kafka/Kubernetes” mà không có timing và bằng chứng, chấm tối đa 4/10.
