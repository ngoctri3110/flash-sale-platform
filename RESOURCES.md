# Senior Java Spring Boot Backend Resources

## Knowledge

- [Spring Boot Reference](https://docs.spring.io/spring-boot/reference/)
  Tài liệu chính thức về cấu hình, testing, observability, packaging và production features. Dùng xuyên suốt project.
- [Spring MVC: Annotated Controllers](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller.html)
  Contract HTTP, request mapping, request/response handling. Dùng cho REST API design.
- [Spring MVC: Validation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)
  Phân biệt object validation và method validation cùng các exception tương ứng. Dùng cho validation và error handling.
- [Spring MVC: Exceptions](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html)
  Cơ chế `@ExceptionHandler` và `@ControllerAdvice`. Dùng để thiết kế error response thống nhất.
- [Spring Framework: Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
  Transaction abstraction, declarative/programmatic transaction và proxy semantics. Dùng cho transaction boundary và rollback.
- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/reference/jpa.html)
  Repository, query method, transactionality, locking và persistence concerns. Dùng cho tầng data access.
- [Spring Data JPA: Locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html)
  Lock mode trên repository query. Dùng khi so sánh optimistic và pessimistic locking.
- [Spring Data: Scrolling](https://docs.spring.io/spring-data/commons/reference/repositories/scrolling.html)
  Offset và keyset scrolling. Dùng cho pagination trên tập dữ liệu lớn.
- [PostgreSQL Current Documentation: SQL, Concurrency and Performance](https://www.postgresql.org/docs/current/sql.html)
  Nguồn chuẩn cho isolation, explicit locking, indexes, `EXPLAIN` và query planner.
- [Flyway: Migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations)
  Versioned/repeatable migration, schema history và execution rules. Dùng cho schema evolution.
- [Apache Kafka: Design](https://kafka.apache.org/40/design/design/)
  Partitioning, ordering, delivery semantics, idempotent producer và transactions. Dùng trước khi viết producer/consumer.
- [Spring for Apache Kafka](https://docs.spring.io/spring-kafka/reference/kafka.html)
  Producer, listener container, error handling, transactions và exactly-once semantics trong Spring.
- [Docker Java Guide](https://docs.docker.com/guides/java/)
  Containerize Java/Spring Boot, development workflow và Compose.
- [Docker: Multi-stage Builds](https://docs.docker.com/build/building/multi-stage/)
  Tách build/runtime stage để giảm image size và attack surface.
- [Kubernetes Concepts](https://kubernetes.io/docs/concepts/overview/)
  Nền tảng về workload, networking, configuration, self-healing và scaling.
- [Kubernetes: Liveness, Readiness and Startup Probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-probes/)
  Dùng để thiết kế lifecycle đúng cho JVM application.
- [Kubernetes: Horizontal Pod Autoscaling](https://kubernetes.io/docs/concepts/workloads/autoscaling/horizontal-pod-autoscale/)
  Cơ chế HPA, metrics và ảnh hưởng của readiness khi autoscale.
- [Testcontainers for Java](https://java.testcontainers.org/)
  Integration test với PostgreSQL/Kafka thật trong disposable containers.
- [Patterns of Enterprise Application Architecture](https://martinfowler.com/eaaCatalog/)
  Các pattern về service layer, domain model, data mapper và transaction script. Dùng để thảo luận trade-off kiến trúc.

## Wisdom (Communities)

- [Spring: Stack Overflow guidance](https://spring.io/questions)
  Điểm vào do Spring duy trì để đặt câu hỏi kỹ thuật có ví dụ tái hiện rõ ràng.
- [PostgreSQL Community](https://www.postgresql.org/community/)
  Mailing lists và kênh cộng đồng chính thức. Dùng khi cần phản biện query plan hoặc concurrency behavior.
- [Apache Kafka Community](https://kafka.apache.org/contact)
  Mailing lists và kênh thảo luận chính thức. Dùng cho câu hỏi semantics và vận hành Kafka.

## Gaps

- Chưa chọn một sách Java concurrency chuyên sâu; sẽ bổ sung khi bắt đầu module race condition nếu cần nền tảng Java Memory Model.
- Chưa chọn môi trường Kubernetes local (`kind`, `minikube` hay Docker Desktop); quyết định ở đầu module triển khai.
