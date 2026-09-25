# Lab 11 — Phân biệt transaction qua các thời điểm lỗi

Lab này không dạy thêm annotation. Nó buộc bạn trả lời: **đã commit ở đâu, rollback được gì, và repair bằng gì?**

## 1. Database rollback

```powershell
cd backend
.\mvnw.cmd "-Dtest=OrderApiIntegrationTest#inventoryDecrementRollsBackWhenOrderInsertionFails" test
```

Trong IntelliJ đặt breakpoint ở `decrementAvailableQuantity`, bước đến lỗi insert. Ghi stock trước/sau, Order count và outbox count.

## 2. DB commit nhưng response mất

Gửi POST Order với Idempotency-Key, ngắt/giả lập client không đọc response, rồi gửi lại **cùng key**. So sánh Order ID, inventory và outbox. Đây là business retry/replay, không phải DB rollback.

## 3. DB commit nhưng Kafka down

Đọc [OutboxPublisherIntegrationTest](../../backend/src/test/java/com/ngoctri/flashsale/messaging/OutboxPublisherIntegrationTest.java). Điền:

```text
orders: ?
inventory: ?
outbox: ?
kafka: ?
repair: ?
```

Expected: Order/inventory/outbox commit; Kafka chưa có; publisher retry.

## 4. Consumer duplicate và poison

```powershell
.\mvnw.cmd "-Dtest=OrderEventConsumerIntegrationTest" test
```

Ghi kết quả khi event ID lặp và khi schema/event version không hợp lệ. Duplicate phải no-op; poison phải retry hữu hạn/DLT, không làm consumer loop vô hạn.

## 5. Bảng bắt buộc

| Scenario | Local DB commit? | External effect? | Rollback được gì? | Hành động |
|---|---|---|---|---|
| Insert Order fail | No | No | DB writes | rollback/retry |
| HTTP response mất | Yes | outbox yes | không rollback | replay key |
| Kafka down | Yes | Kafka no | không rollback Order | outbox retry |
| Kafka duplicate | consumer tùy thời điểm | event đã có | consumer DB transaction | dedup |
| Poison payload | producer state unchanged | event không xử lý | không cần rollback Order | DLT/replay |

## Output gửi mentor

Gửi bảng trên, một sequence diagram và câu trả lời: “Transaction nào đang nói tới trong từng scenario?”
