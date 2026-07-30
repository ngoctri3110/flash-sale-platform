# Playbook 11: Consumer idempotency, retry và DLQ

## Rule

> Consumer nhận cùng event hai lần vẫn chỉ tạo side effect một lần.

Đọc [OrderCreatedEventConsumer](../../backend/src/main/java/com/ngoctri/flashsale/messaging/infrastructure/OrderCreatedEventConsumer.java), migration [V6 processed events](../../backend/src/main/resources/db/migration/V6__create_processed_events_and_order_event_audit.sql) và [consumer integration test](../../backend/src/test/java/com/ngoctri/flashsale/messaging/OrderEventConsumerIntegrationTest.java).

```text
receive event E → attempt INSERT processed_events(E)
  inserted → execute business side effect + commit
  duplicate key → acknowledge/no-op
  transient failure → bounded backoff retry
  poison payload → DLQ + alert + replay procedure
```

## Bài thực hành

1. Publish cùng event ID hai lần; audit row chỉ được một.
2. Phân loại failure transient (DB timeout) và permanent (schema/payload sai).
3. Thiết kế DLQ envelope: original payload, topic/partition/offset, error type, attempts, timestamp, correlation ID.
4. Viết runbook replay: ai được phép replay, replay idempotent thế nào, metric nào theo dõi.

## Không làm

- Retry vô hạn trong consumer thread.
- Acknowledge trước khi durable side effect commit.
- Dùng Kafka offset một mình làm dedup key giữa nhiều topic/replay.

## Interview

“At-least-once bắt buộc consumer idempotent. Tôi lưu/claim event ID cùng transaction với side effect, retry có backoff và đưa poison event vào DLQ có replay runbook.”
