# Lab 09 — Hai service, request xuyên service và partial failure

Lab này tách một flow nhỏ thành `catalog-service` và `order-service` để nhìn thấy chi phí microservice. Đây là bước trước khi thêm Spring Cloud Config/Gateway/Resilience4j.

## Chạy

Mở hai cửa sổ IntelliJ hoặc terminal:

```powershell
cd playbooks/09-microservice-request-lab/catalog-service
mvn spring-boot:run
```

```powershell
cd playbooks/09-microservice-request-lab/order-service
mvn spring-boot:run
```

Gọi:

```powershell
Invoke-RestMethod http://localhost:8081/products/1
Invoke-RestMethod -Method Post -Uri http://localhost:8082/orders -ContentType 'application/json' -Body '{"productId":1,"quantity":1}'
```

`order-service` gọi `catalog-service` qua HTTP. Dừng catalog rồi gọi order lại: request phải trả lỗi downstream, không giả vờ tạo order thành công. Đây là partial failure thật của microservice.

## Bài làm tiếp

1. Đưa URL catalog vào `application-local.yml`, không hard-code trong Java.
2. Thêm timeout 500 ms và map timeout thành `503 CATALOG_UNAVAILABLE`.
3. Truyền `X-Trace-Id` từ order sang catalog.
4. Thêm contract test để catalog đổi field không làm order compile/test sai.
5. Thêm Spring Cloud Config Server, Gateway và Resilience4j **sau** khi lab cơ bản chạy; mỗi component phải có failure test riêng.

## Checkpoint

Vẽ hai thread/request, hai process, hai log stream và các connection pool riêng. Giải thích vì sao retry POST vô điều kiện có thể tạo duplicate order, và vì sao transaction local không bao trùm được hai service.
