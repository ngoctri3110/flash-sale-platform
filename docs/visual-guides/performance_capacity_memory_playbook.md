# Playbook: performance, capacity và memory budget

## Từ latency đến capacity

Đừng nói “scale thêm pod” trước khi tách: CPU time, DB time, downstream time, queue wait, serialization và lock wait.

Một capacity note tối thiểu phải ghi:

```text
request rate, concurrency, p95/p99, error rate,
HTTP threads, DB pool active/pending, query time,
heap/GC, Kafka lag, lock wait
```

## Bài thực hành

1. Chạy Compose với workload 10/50/100 concurrent requests.
2. Ghi baseline p50/p95/p99 và error rate.
3. Giảm Hikari pool, tạo query chậm, tăng response payload; mỗi lần chỉ đổi một biến.
4. Dùng JFR/thread dump/heap histogram để xác định queue, object hoặc GC.
5. Chạy N+1/query-plan lab trước khi tăng replicas.
6. Viết capacity decision: bottleneck hiện tại, giới hạn an toàn, metric cảnh báo, bước scale tiếp theo.

## Rule

Connection pool không nên đặt bằng số thread một cách máy móc. Hàng nghìn virtual threads vẫn chờ pool nhỏ. HPA CPU có thể bỏ sót DB queue; scale app có thể làm DB tệ hơn. Cache không được biến stale price/stock thành correctness bug.

## Interview

“Tôi đo p95/p99 và queue/pool/lock trước khi tối ưu. Tôi giữ một biến thay đổi mỗi lần, so sánh cùng workload, và ghi capacity limit. Scale là quyết định dựa trên bottleneck, không phải phản xạ tăng replica.”
