# Playbook: Spring Cloud microservices vừa đủ

## Tách khi nào?

Chỉ tách khi có boundary ownership, deploy cadence, scale hoặc failure isolation rõ ràng. Mỗi service có process, thread pool, connection pool, config, deploy và data boundary riêng; microservices tăng failure mode.

## Lab hai service

Tạo trong IntelliJ hai Spring Boot project Java 21:

1. `catalog-service`: `GET /products/{id}`.
2. `order-service`: `POST /orders`, gọi catalog qua `WebClient`/HTTP client có timeout 500 ms.
3. Chạy cả hai trên port khác nhau; order trả lỗi domain khi catalog timeout.
4. Thêm externalized config bằng Spring Cloud Config hoặc profile/config file trước; không hard-code URL.
5. Thêm gateway chỉ khi cần routing/auth/cross-cutting; không dùng gateway để che logic business.
6. Truyền `X-Trace-Id` qua request; viết contract test cho response catalog.

## Các mảnh Spring Cloud cần học theo failure

| Nhu cầu | Công cụ/pattern | Bài kiểm chứng |
|---|---|---|
| Config tập trung | Config Server hoặc platform config | đổi URL/config không rebuild image |
| Routing/auth | Spring Cloud Gateway | route, timeout, header propagation |
| Service discovery | Eureka/Kubernetes DNS | service restart không hard-code IP |
| Failure control | Resilience4j timeout/circuit breaker/bulkhead | catalog down không kéo order thread cạn |
| Contract | OpenAPI/consumer-driven contract | provider đổi field vẫn giữ client cũ |

## Không được bỏ qua

Retry phải có timeout, budget, jitter và idempotency; retry cả POST không an toàn. Circuit breaker không sửa lỗi dữ liệu. Mỗi service cần metric riêng cho inflight request, pool DB, downstream latency và rejected calls. Nếu Order và Inventory cần transaction cùng database invariant, giữ boundary trong modular monolith hoặc thiết kế reservation/saga có compensation rõ ràng.

## Interview

“Tôi thiết kế boundary trước rồi mới chọn Spring Cloud component. Tôi chứng minh timeout, retry budget, partial failure, contract compatibility và trace propagation; service discovery/config không tự làm hệ thống resilient.”
