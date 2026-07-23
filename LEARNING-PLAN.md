# Learning Plan: Flash Sale Order & Inventory Platform

## Cách học

Mỗi module đi theo vòng lặp: đọc nguồn chính thống → dự đoán hành vi → viết test hoặc contract → triển khai phần nhỏ nhất → quan sát kết quả → giải thích lại không nhìn tài liệu. Không chuyển module chỉ vì code chạy; phải trả lời được checkpoint và chỉ ra bằng chứng.

Kiến trúc học tập bắt đầu bằng một Spring Boot modular monolith cùng PostgreSQL. Kafka, Docker và Kubernetes được thêm sau khi invariant đặt hàng và transaction boundary đã ổn định.

## Module 0 — Baseline Java, Spring Boot và cấu trúc project

### Kiến thức cần học

- Dependency injection, configuration properties, profiles và lifecycle của Spring application context.
- Maven/Gradle lifecycle, dependency scopes và cấu trúc test.
- Package-by-feature, dependency direction và ranh giới giữa web, application, domain, infrastructure.
- Java records, immutability, exceptions và collection APIs cần dùng trong project.

### Phần code sẽ xây

- Khởi tạo backend Spring Boot với Java LTS, build wrapper và health endpoint.
- Tạo các feature package ban đầu: `product`, `inventory`, `order`, `shared`.
- Cấu hình profiles `local` và `test`; tạo smoke test khởi động context.

### Bài tập kiểm tra hiểu

- Vẽ dependency direction giữa controller, use case, domain và repository adapter; giải thích import nào bị cấm.
- Tự thêm một configuration property có validation và test trường hợp thiếu giá trị.
- Chọn một class do AI sinh và giải thích vai trò của từng dependency constructor.

### Câu hỏi phỏng vấn liên quan

- `@SpringBootApplication` tổng hợp những cơ chế nào?
- Vì sao constructor injection thường tốt hơn field injection?
- Package-by-layer và package-by-feature khác nhau thế nào khi codebase lớn?

## Module 1 — Domain model và REST API contract

### Kiến thức cần học

- Resource-oriented API, HTTP methods, status codes, idempotency và URI design.
- Phân biệt DTO, command, domain entity và persistence entity.
- Invariant cốt lõi: stock không âm, số lượng mua dương, order có trạng thái hợp lệ.
- Backward compatibility và API evolution.

### Phần code sẽ xây

- Product APIs: tạo, xem chi tiết và liệt kê sản phẩm.
- Product administration APIs: tạo/sửa sản phẩm và điều chỉnh inventory; dữ liệu local/demo được seed sẵn.
- Order API contract: `POST /orders` với `Idempotency-Key`, chưa xử lý concurrency ở module này.
- OpenAPI contract và các request/response DTO độc lập với JPA entity.

### Bài tập kiểm tra hiểu

- Viết bảng contract gồm happy path, invalid input, not found, conflict và duplicate request.
- Giải thích vì sao `POST /orders` cần idempotency dù client chỉ bấm một lần.
- Phản biện một controller AI-generated đang trả thẳng JPA entity.

### Câu hỏi phỏng vấn liên quan

- `PUT` và `PATCH` khác nhau về semantics và idempotency thế nào?
- Khi nào trả `400`, `404`, `409` và `422`?
- Tại sao không nên expose persistence entity qua REST?

## Module 2 — Validation và error handling chuẩn

### Kiến thức cần học

- Jakarta Bean Validation: field, nested, method và custom constraint.
- Sự khác nhau giữa input validation, business rule và database constraint.
- `@ControllerAdvice`, exception mapping, Problem Details và correlation ID.
- Không làm rò rỉ stack trace, SQL hoặc thông tin nội bộ ra client.

### Phần code sẽ xây

- Validation cho product, campaign và order request.
- Custom validator cho thời gian flash sale và giới hạn mua.
- Global exception handler với error envelope ổn định: code, message, field errors, trace ID.
- MVC tests kiểm tra chính xác HTTP status và response body.

### Bài tập kiểm tra hiểu

- Phân loại 12 lỗi giả định vào DTO validation, domain validation hoặc DB constraint.
- Viết test cho nested validation và hai exception validation khác nhau của Spring MVC.
- Tìm ba lỗi bảo mật/khả năng bảo trì trong một error handler do AI tạo.

### Câu hỏi phỏng vấn liên quan

- `@Valid` và `@Validated` khác nhau ở đâu?
- Vì sao vẫn cần database constraint khi đã validate ở application?
- Bạn thiết kế error contract ổn định qua nhiều phiên bản API thế nào?

## Module 3 — PostgreSQL schema và Flyway migration

### Kiến thức cần học

- Relational modeling, primary/foreign key, unique/check constraint và kiểu dữ liệu phù hợp.
- Versioned migration, repeatable migration, checksum và forward-only evolution.
- Zero-downtime schema change cơ bản: expand → migrate → contract.
- Testcontainers để kiểm thử trên PostgreSQL thật.

### Phần code sẽ xây

- Docker Compose cho PostgreSQL local.
- Flyway migrations tạo `products`, `inventories`, `orders`, `inventory_adjustments`, `outbox_events` và `processed_events`.
- Constraints cho money, quantity, trạng thái và uniqueness của idempotency key.
- Integration test chạy migrations trên PostgreSQL Testcontainer từ database rỗng.

### Bài tập kiểm tra hiểu

- Viết migration thêm cột bắt buộc vào bảng lớn mà không phá phiên bản app đang chạy.
- Cố tình sửa migration đã chạy, quan sát checksum failure và giải thích cách xử lý đúng.
- So sánh hậu quả của `numeric`, `double precision` và integer minor units cho tiền.

### Câu hỏi phỏng vấn liên quan

- Vì sao không nên dùng `ddl-auto=update` ở production?
- Làm sao rollback một schema change khi database đã có dữ liệu mới?
- Flyway schema history đảm bảo điều gì và không đảm bảo điều gì?

## Module 4 — JPA, persistence context và transaction boundary

### Kiến thức cần học

- Entity states, persistence context, dirty checking, flush và lazy loading.
- Transaction atomicity, propagation, isolation, rollback rules và AOP proxy.
- Self-invocation pitfall của `@Transactional`; transaction boundary ở application service.
- N+1 query, fetch join, entity graph và projection.

### Phần code sẽ xây

- JPA mappings và repositories cho catalog, inventory, order.
- Use case đặt hàng nguyên tử: snapshot giá, giảm inventory, tạo order một sản phẩm và ghi outbox trong một transaction.
- Query có projection cho order summary; integration tests kiểm tra rollback.
- Logging SQL có kiểm soát để quan sát flush và N+1.

### Bài tập kiểm tra hiểu

- Dự đoán số câu SQL của một use case trước khi chạy test rồi đối chiếu log.
- Tạo lỗi sau bước trừ stock và chứng minh toàn bộ transaction rollback.
- Sửa một ví dụ `@Transactional` self-invocation mà không kéo transaction lên controller.

### Câu hỏi phỏng vấn liên quan

- Khi nào JPA thực sự gửi `UPDATE` xuống database?
- `REQUIRED` và `REQUIRES_NEW` khác nhau thế nào?
- Vì sao `@Transactional` có thể không hoạt động khi gọi method trong cùng class?

## Module 5 — Race condition, locking và chống overselling

### Kiến thức cần học

- Lost update, write skew, isolation anomalies và lock wait/deadlock.
- Optimistic locking với version; pessimistic row lock với `SELECT ... FOR UPDATE`.
- Atomic conditional update: giảm stock khi `available >= requested`.
- Throughput, contention, fairness và retry strategy; invariant quan trọng hơn annotation.

### Phần code sẽ xây

- Concurrency test phát hiện overselling ở implementation ngây thơ.
- Ba chiến lược inventory: optimistic lock, pessimistic lock và atomic SQL update.
- Mapping lock timeout/deadlock/insufficient stock thành kết quả nghiệp vụ phù hợp.
- Benchmark nhỏ so sánh correctness, latency và throughput dưới contention.

### Bài tập kiểm tra hiểu

- Chạy 100 buyer đồng thời cho 10 sản phẩm và chứng minh: đúng 10 success, stock cuối bằng 0.
- Vẽ interleaving tạo lost update và chỉ ra điểm linearization của từng giải pháp.
- Chọn chiến lược locking cho stock nóng; viết decision note nêu trade-off và failure mode.

### Câu hỏi phỏng vấn liên quan

- Optimistic và pessimistic locking phù hợp với workload nào?
- Isolation level cao hơn có tự động ngăn mọi dạng overselling không?
- Deadlock xảy ra thế nào và application nên retry ra sao?

## Module 6 — Testing strategy cho business-critical backend

### Kiến thức cần học

- Test pyramid, unit/integration/contract/concurrency test và mục đích khác nhau.
- Deterministic test, test data builder, clock abstraction và tránh over-mocking.
- Testcontainers cho PostgreSQL/Kafka; transaction behavior trong test.
- TDD red–green–refactor cho invariant rủi ro cao.

### Phần code sẽ xây

- Unit tests cho order state machine và inventory policy.
- Repository/integration tests trên PostgreSQL thật.
- API tests cho error contract và idempotency.
- Bộ concurrency regression test chạy lặp để bắt race condition.

### Bài tập kiểm tra hiểu

- Với mỗi test hiện có, nói rõ bug nào test có thể bắt và bug nào không thể bắt.
- Thay một mock-based repository test bằng Testcontainers và so sánh giá trị nhận được.
- Làm mutation nhỏ phá invariant rồi kiểm tra test suite có đỏ không.

### Câu hỏi phỏng vấn liên quan

- Vì sao test dùng H2 có thể xanh nhưng production PostgreSQL vẫn lỗi?
- Khi nào dùng mock, fake và real dependency?
- Làm sao kiểm thử race condition mà giảm flaky test?

## Module 7 — Pagination, indexing và query optimization

### Kiến thức cần học

- Offset pagination, keyset pagination, stable ordering và API cursor.
- B-tree/composite/partial/covering index; leftmost-prefix và selectivity.
- `EXPLAIN (ANALYZE, BUFFERS)`, planner statistics và chi phí đọc/ghi của index.
- Query count, N+1, projections và giới hạn dữ liệu trả về.

### Phần code sẽ xây

- Product/order listing với bounded page size, allowlisted sort và stable response DTO.
- Keyset pagination cho order history lớn.
- Migrations thêm index dựa trên query thực tế.
- Seed dataset đủ lớn và lưu before/after query plans để chứng minh tối ưu.

### Bài tập kiểm tra hiểu

- Đọc một query plan, tìm bottleneck và dự đoán index phù hợp trước khi tạo.
- So sánh latency page đầu và page sâu giữa offset với keyset.
- Xóa một index, đo regression và giải thích vì sao planner đổi kế hoạch.

### Câu hỏi phỏng vấn liên quan

- Vì sao index `(status, created_at)` không luôn thay thế được `(created_at, status)`?
- Khi nào keyset pagination tốt hơn offset và đánh đổi điều gì?
- Vì sao thêm nhiều index có thể làm hệ thống chậm hơn?

## Module 8 — Kafka và event-driven architecture đáng tin cậy

### Kiến thức cần học

- Topic, partition, key, ordering, consumer group, offset và rebalance.
- At-most-once, at-least-once, idempotency và phạm vi thật của exactly-once.
- Dual-write problem; transactional outbox và relay/publisher.
- Retry with backoff, dead-letter topic, poison message và schema evolution.

### Phần code sẽ xây

- Ghi `OrderCreated` vào outbox cùng transaction tạo order.
- Publisher đẩy outbox event lên Kafka; partition key theo `orderId` hoặc aggregate cần ordering.
- Consumer idempotent cho notification/analytics với processed-event store.
- Integration tests bằng Kafka Testcontainer cho duplicate, retry và out-of-order scenarios.

### Bài tập kiểm tra hiểu

- Vẽ failure matrix cho DB commit/Kafka publish và chứng minh outbox xử lý từng trường hợp.
- Gửi cùng event hai lần và chứng minh consumer side effect chỉ xảy ra một lần.
- Thay partition key rồi dự đoán tác động đến ordering và throughput.

### Câu hỏi phỏng vấn liên quan

- Tại sao publish Kafka sau `save()` vẫn có thể mất event?
- Kafka exactly-once có làm side effect vào PostgreSQL tự động exactly-once không?
- Consumer rebalance ảnh hưởng xử lý message đang chạy thế nào?

## Module 9 — Clean code, SOLID và design patterns có lý do

### Kiến thức cần học

- Cohesion, coupling, dependency inversion và deep module interfaces.
- SOLID như công cụ đánh giá changeability, không phải checklist tạo thêm class.
- Service Layer, Repository, Strategy, State, Factory và Outbox; khi nào pattern gây overengineering.
- Domain exceptions, value objects và explicit invariants.

### Phần code sẽ xây

- Refactor order flow thành application use case với domain policy rõ ràng.
- Strategy cho inventory reservation; State/transition rules cho order lifecycle.
- Ports/adapters chỉ tại boundary PostgreSQL, Kafka và clock/ID generation.
- Architecture tests ngăn dependency sai hướng.

### Bài tập kiểm tra hiểu

- Đề xuất hai thiết kế khác nhau cho inventory reservation và so sánh change cost.
- Xóa một abstraction không tạo giá trị; chứng minh code dễ hiểu hơn mà test vẫn xanh.
- Review một PR AI-generated, chỉ ra smell dựa trên coupling/invariant thay vì sở thích style.

### Câu hỏi phỏng vấn liên quan

- Dependency inversion khác dependency injection thế nào?
- Khi nào rich domain model tốt hơn transaction script?
- Bạn nhận biết abstraction quá sớm bằng dấu hiệu nào?

## Module 10 — Docker, cấu hình và production readiness

### Kiến thức cần học

- Image layers, multi-stage build, build cache, non-root user và immutable image.
- Docker Compose networking, volumes, health checks và dependency readiness.
- Externalized configuration, secrets và 12-factor boundaries.
- Spring Boot Actuator, structured logs, metrics và graceful shutdown.

### Phần code sẽ xây

- Multi-stage Dockerfile cho backend và image runtime non-root.
- Compose stack gồm app, PostgreSQL và Kafka cho local integration.
- Actuator health/readiness, metrics cơ bản và correlation ID trong logs.
- Smoke test chạy trên image thay vì chỉ chạy từ IDE.

### Bài tập kiểm tra hiểu

- So sánh image size/layers trước và sau multi-stage optimization.
- Kill PostgreSQL/Kafka trong lúc chạy và ghi lại cách app fail/recover.
- Tìm secret bị bake vào image/config và sửa thành runtime injection.

### Câu hỏi phỏng vấn liên quan

- `depends_on` khác readiness như thế nào?
- Vì sao container nên chạy non-root và có image cố định theo digest/tag?
- Liveness và readiness của Spring Boot service nên phản ánh điều gì?

## Module 11 — Kubernetes deployment và scaling

### Kiến thức cần học

- Pod, Deployment, Service, ConfigMap, Secret, requests/limits và rollout.
- Startup, liveness và readiness probes cho JVM application.
- Horizontal Pod Autoscaler, metrics lag và mối quan hệ với bottleneck database.
- Stateless app, graceful termination và disruption during rolling deployment.

### Phần code sẽ xây

- Kubernetes manifests hoặc Kustomize base cho backend.
- Deployment/Service/ConfigMap/Secret references, probes và resource budgets.
- HPA cho API; local cluster deployment và rolling update test.
- Load test nhỏ chứng minh scale-out không phá invariant stock.

### Bài tập kiểm tra hiểu

- Cố tình cấu hình sai probe, quan sát restart loop và sửa dựa trên evidence.
- Rolling update khi đang đặt hàng; kiểm tra request failure và duplicate behavior.
- Giải thích vì sao tăng replicas app có thể làm database chậm hơn.

### Câu hỏi phỏng vấn liên quan

- Deployment, StatefulSet và Job khác nhau ở điểm nào?
- Requests/limits ảnh hưởng scheduling và runtime ra sao?
- HPA dựa trên CPU có phù hợp với flash sale không; metric nào tốt hơn?

## Module 12 — Đọc hiểu code AI và capstone senior review

### Kiến thức cần học

- AI output là giả thuyết cần kiểm chứng: contract, invariant, transaction, security và operability.
- Truy vết call path từ HTTP → use case → DB/Kafka và xác định side effects.
- Review theo risk: correctness trước, concurrency/data loss sau, maintainability và style cuối.
- Dùng tests, SQL plans, logs, metrics và tài liệu chính thống làm bằng chứng.

### Phần code sẽ xây

- Checklist review AI áp dụng cho mọi pull request của project.
- Architecture/domain documentation giải thích order flow và inventory invariant.
- Load/concurrency report với throughput, p95/p99, error rate và database evidence.
- Capstone release chạy local end-to-end: React → REST → PostgreSQL/outbox → Kafka consumer, đóng gói và deploy.

### Bài tập kiểm tra hiểu

- Với một feature do AI sinh, viết lại bằng lời: input, output, invariant, side effects và failure modes trước khi chạy.
- Bỏ một annotation hoặc thay isolation/lock strategy; dự đoán rồi kiểm chứng tác động.
- Thực hiện mock interview 45 phút: system design, code review và incident overselling.

### Câu hỏi phỏng vấn liên quan

- Bạn làm gì để tin rằng code AI-generated xử lý transaction đúng?
- Hãy điều tra incident overselling dù unit tests đều xanh.
- Thiết kế flash sale cho tải tăng 100 lần: bottleneck, trade-off và rollout plan là gì?

## Cổng hoàn thành từng module

Một module chỉ hoàn thành khi có đủ bốn bằng chứng:

1. Code hoặc tài liệu quyết định đã được review và test phù hợp.
2. Người học giải thích được luồng chính mà không đọc lại code.
3. Ít nhất một bài tập retrieval được trả lời sau một khoảng nghỉ.
4. Có thể nêu một trade-off và một failure mode của giải pháp đã chọn.

Sau mỗi module, chỉ tạo learning record khi có bằng chứng hiểu thật sự; không ghi nhận chỉ vì đã đọc hoặc đã chạy được code.
