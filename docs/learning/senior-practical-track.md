# Senior Java/Spring thực chiến: học từ sự cố đến thiết kế

Mục tiêu của track này là làm được một feature thật, nhìn thấy lỗi, sửa bằng test và giải thích được quyết định. Mỗi bài chỉ học phần thường gặp trong dự án; phần Spring không có lý do sử dụng sẽ để sau.

## Cách học bắt buộc

```text
Pain production → invariant → code nhỏ → chạy lab → cố ý làm hỏng → sửa bằng test → đo → trả lời phỏng vấn
```

Một bài đạt khi bạn có: kết quả chạy, diff tự viết, một failure đã tái hiện, test bảo vệ và câu trả lời trade-off 60 giây. Đọc xong không được tính là hoàn thành.

## Bài 00 — Tạo project và debug trong IntelliJ

### Làm từ project trống

1. IntelliJ IDEA → **New Project → Spring Initializr**.
2. Java 21, Maven, Spring Boot stable đang hiển thị trong Initializr.
3. Dependencies: Spring Web, Validation, Actuator, Spring Data JPA, PostgreSQL Driver, Flyway, Spring Boot Test.
4. Tạo package `com.example.demo`; bật Maven auto-import; chạy `DemoApplication` bằng nút Run.
5. Thêm controller tối thiểu:

```java
@RestController
class PingController {
    @GetMapping("/ping")
    Map<String, String> ping() {
        return Map.of("status", "ok");
    }
}
```

6. Gọi `http://localhost:8080/ping`, đặt breakpoint trong `ping()`, xem Call Stack và Variables.
7. Tạo test `PingControllerTest`; chạy test bằng gutter icon trong IntelliJ.
8. Tạo profile `local`, thêm `server.port`, bật Actuator health, rồi chạy lại bằng Run Configuration.

Bạn phải biết request vào controller nào, thread nào xử lý, bean được inject ở đâu và test chạy bằng classpath nào. Có thể đối chiếu với [backend hiện tại](../../backend) sau khi tự tạo xong.

## Bài 01 — Request → thread → connection → database

### Nỗi đau

API p95 tăng, CPU thấp nhưng request timeout. Thường request đang chờ thread pool, connection pool hoặc lock DB.

### Lab

1. Chạy backend Compose và mở Actuator metrics.
2. Gửi 100 request đồng thời tới một endpoint có query chậm.
3. Quan sát HTTP worker threads, Hikari active/pending connections, PostgreSQL sessions, lock waits và heap.
4. Giảm connection pool xuống 5; chạy lại và ghi p95/error rate.

### Phải hiểu

Thread và DB connection là hai resource khác nhau. Một thread có thể chờ connection; một connection có thể bị giữ bởi query/lock. Virtual threads vẫn chờ nếu pool DB có 5 connections. Memory tăng do request queue, object/response buffering, persistence context và cache, không chỉ do “nhiều thread”.

## Bài 02 — Transaction và concurrency

Nỗi đau: stock âm hoặc bán vượt dù unit test xanh. Chạy [atomic inventory lab](../../playbooks/01-atomic-inventory-lab/README.md), [locking lab](../visual-guides/inventory_locking_strategies_playbook.md) và test PostgreSQL thật. Invariant phải gồm accepted quantity và stock cuối, không chỉ kiểm tra stock âm.

## Bài 03 — JPA, SQL và performance

Nỗi đau: page 20 chạy được, page 500 timeout. Chạy [N+1 playbook](../visual-guides/jpa_n_plus_one_fetch_plan_playbook.md), [JPA/JDBC decision](../visual-guides/jpa_jdbc_native_sql_playbook.md) và [EXPLAIN lab](../../playbooks/07-postgresql-query-plan-lab/README.md). Mọi tối ưu cần query count, query plan hoặc p95 trước/sau.

## Bài 04 — API contract và frontend an toàn

Nỗi đau: FE hiển thị sai khi retry, lỗi validation không thống nhất, hoặc client cũ vỡ sau deploy. Làm [API/FE contract playbook](../visual-guides/api_frontend_contract_playbook.md).

## Bài 05 — Redis đúng mục đích

Nỗi đau: thêm Redis để nhanh nhưng stale price, mất lock hoặc rate limit sai khi có nhiều pod. Làm [Redis playbook](../visual-guides/redis_cache_rate_limit_lock_playbook.md) và [Redis lab](../../playbooks/08-redis-production-lab/README.md). Redis là accelerator/coordination component; PostgreSQL vẫn là source of truth cho order/inventory.

## Bài 06 — Đồng bộ, bất đồng bộ và event-driven

Nỗi đau: request chậm vì gọi notification/payment; hoặc Kafka down làm mất event. Chạy [outbox playbook](../visual-guides/transactional_outbox_kafka_playbook.md) và [consumer playbook](../visual-guides/consumer_idempotency_retry_dlq_playbook.md). Chọn synchronous khi caller cần kết quả/consistency ngay; asynchronous khi side effect có thể eventual và cần tách latency/failure. Luôn nêu retry, duplicate, ordering và DLQ.

## Bài 07 — Microservice Spring Cloud vừa đủ

Nỗi đau: tách service nhưng timeout, config drift, retry storm và debug không biết request đang ở đâu. Làm [Spring Cloud playbook](../visual-guides/spring_cloud_microservices_playbook.md) sau khi modular monolith đã có boundary.

## Bài 07A — Failure handling đồng bộ và bất đồng bộ

Học [failure handling playbook](../visual-guides/failure_handling_sync_async_playbook.md) và chạy [Lab 10](../../playbooks/10-failure-handling-lab/README.md). Phải phân biệt lỗi buộc dừng, lỗi retry, lỗi best-effort log/continue, fallback, compensation và poison message/DLQ. Không được `catch Exception rồi trả success` cho Order/payment/inventory.

## Bài 07B — Transaction taxonomy

Đọc [Transaction taxonomy](../visual-guides/transaction_taxonomy_and_scenarios_playbook.md) và chạy [Lab 11](../../playbooks/11-transaction-scenarios-lab/README.md). Phải phân biệt database transaction, Spring transaction, business transaction, HTTP request, Java thread, Kafka delivery và distributed workflow. Với mỗi failure, ghi rõ đã commit ở đâu, rollback được gì và repair bằng gì.

## Bài 08 — Security, delivery và operations

Chạy security threat model, Docker/metrics, Kind rollout và incident runbook. Đọc [security](../visual-guides/backend_security_pii_playbook.md), [operations](../visual-guides/docker_observability_runbook_playbook.md) và [Kubernetes](../visual-guides/kubernetes_delivery_scaling_playbook.md).

## Bài 09 — Capstone booking hoặc finance

Booking cần state machine, hold expiry và release idempotent. Finance cần immutable double-entry ledger, reconciliation và authorization. Dùng [booking](../visual-guides/booking_reservation_state_machine_playbook.md) hoặc [ledger](../visual-guides/finance_double_entry_ledger_playbook.md); không copy `Order` nguyên xi.

## Bài 10 — Các khoảng bắt buộc để gọi là Senior production-ready

- [Java/JVM/thread/memory](../visual-guides/java_jvm_thread_memory_playbook.md): thread dump, heap, JFR, queue và connection pool.
- [Spring internals/security](../visual-guides/spring_internals_security_playbook.md): proxy, self-invocation, profiles, authorization và IDOR.
- [Redis Java](../visual-guides/redis_java_implementation_playbook.md): Lua rate limit, cache stampede và lock ownership.
- [Microservice resilience](../visual-guides/microservice_resilience_contract_playbook.md): timeout, retry budget, circuit breaker, bulkhead, contract và trace.
- [Performance/capacity](../visual-guides/performance_capacity_memory_playbook.md): p95/p99, GC, pool pressure và capacity note.
- [Scenario workbook](senior-situation-workbook.md): sáu tình huống phải đạt ≥ 8/10.

## Thứ tự làm và cách mentor review

Làm Bài 00 → 01 → 02 → 03 trước. Mỗi lần gửi: proposal 10 dòng, diff, test output, một failure đã tạo và câu trả lời interview. Mentor review correctness trước, performance sau, style cuối. Khi phần nền đã có evidence mới sang Redis và microservices.

## Những thứ chưa cần học ngay

Không cần học toàn bộ Spring ecosystem, mọi design pattern, mọi Spring Cloud component, CQRS/Event Sourcing hay Kubernetes operator để làm tốt MVP. Chỉ thêm khi một requirement/failure cần nó và bạn đo được giá trị.
