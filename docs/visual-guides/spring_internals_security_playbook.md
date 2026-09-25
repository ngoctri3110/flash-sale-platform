# Playbook: Spring proxy, transaction và security boundary

## Nỗi đau

`@Transactional` có mặt nhưng rollback không xảy ra; profile local vô tình dùng secret production; customer đọc được Order của customer khác.

## Proxy exercise

Tạo service:

```java
@Transactional
public void outer() { inner(); }

@Transactional(propagation = REQUIRES_NEW)
public void inner() { /* write */ }
```

Gọi `outer()` trong cùng instance. Đặt log/transaction probe và chứng minh `inner()` self-invocation không đi qua Spring proxy như một call từ bean khác. Tách `inner()` sang bean riêng, chạy lại và so sánh.

`@Transactional` định nghĩa boundary/rollback qua proxy; nó không biến mọi method call thành transaction. Ghi rõ checked exception, `REQUIRES_NEW`, timeout và rollback-only trong test.

## Security exercise

Viết authorization matrix cho Customer/Admin:

| Endpoint | Customer | Admin |
|---|---|---|
| Product browse | read | read/write |
| Place order | own request | policy-dependent |
| Order detail | own order | all |
| Inventory adjustment | deny | allow + audit |

Test IDOR: Customer A thay `orderId` của B phải nhận 403/404 theo contract, không leak existence/PII. Idempotency key không phải authentication.

## Profile/config exercise

Chạy `local`, `test`, `prod-like` với config khác nhau; kiểm tra secret không nằm trong Git/image/log. Config precedence phải được viết thành README/test, không dựa vào “Spring tự chọn đúng”.

## Interview

“Tôi phân biệt dependency injection với dependency inversion; hiểu annotation chạy qua proxy và có self-invocation pitfall. Security tách authentication khỏi authorization, kiểm tra resource ownership/IDOR và không dùng idempotency key làm identity.”
