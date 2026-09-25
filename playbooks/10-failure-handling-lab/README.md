# Lab 10 — Failure handling: stop, retry, continue, fallback, DLQ

## Phần A — Đồng bộ

Chạy hai service:

```powershell
cd playbooks/09-microservice-request-lab/catalog-service
mvn spring-boot:run
```

```powershell
cd playbooks/09-microservice-request-lab/order-service
mvn spring-boot:run
```

1. Gọi Order khi Catalog khỏe: success.
2. Dừng Catalog: ghi status/error contract. Đây là dependency failure, không phải “log rồi success”.
3. Thêm delay vượt timeout 500ms: retry tối đa 2 lần, sau đó circuit/503.
4. Gửi POST hai lần với cùng idempotency key: retry phải không tạo side effect thứ hai.
5. Gửi payload invalid: dừng ngay, không retry.

## Phần B — Bất đồng bộ

Dùng backend Kafka flow:

```powershell
cd backend
.\mvnw.cmd "-Dtest=OrderEventConsumerIntegrationTest+OutboxPublisherIntegrationTest" test
```

Đọc từng failure và điền bảng:

| Failure | Ack? | Retry? | DLQ? | Side effect |
|---|---|---|---|---|
| DB timeout | No | bounded | sau max attempts | rollback |
| Duplicate event | Yes/no-op | No | No | không lặp |
| Invalid schema | sau publish DLT | No | Yes | không chạy side effect |
| Audit optional fail | tùy contract | bounded/log | tùy | Order không tự rollback nếu đã commit |

## Bài code

Thêm một failure injection sau `processed_events` insert nhưng trước audit insert. Test phải chứng minh transaction rollback và lần retry có thể làm side effect thành công. Sau đó thêm một poison payload và viết expected DLT/runbook.

## Output gửi mentor

Gửi decision table, timeline một sync timeout, timeline một async duplicate, test output và câu trả lời: “Khi nào log rồi đi tiếp là nguy hiểm?”
