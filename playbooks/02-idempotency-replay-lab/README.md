# Lab 02 — Idempotency, replay và concurrent duplicate request

Idempotency nghĩa là: client gửi lại **cùng logical request** vì timeout/retry thì server không tạo side effect lần hai. Nó không có nghĩa mọi request giống nhau đều bị từ chối; scope trong lab là `(customer_id, idempotency_key)`.

## Chuẩn bị

```powershell
cd playbooks/02-idempotency-replay-lab
docker compose up -d
docker compose exec postgres psql -U lab -d idempotency_lab
```

Mở hai terminal `psql`. Dùng chung customer ID `11111111-1111-1111-1111-111111111111`, key `checkout-2026-0001`, product `10`, quantity `1`.

## Phần 1 — Unique constraint là hàng rào cuối, chưa phải replay UX

Trong A và B, gần như cùng lúc chạy:

```sql
INSERT INTO orders (customer_id, product_id, quantity, idempotency_key)
VALUES ('11111111-1111-1111-1111-111111111111', 10, 1, 'checkout-2026-0001');
```

Một terminal insert thành công; terminal còn lại báo `duplicate key`. Kiểm tra:

```sql
SELECT id, customer_id, product_id, quantity, idempotency_key FROM orders;
```

Unique constraint bảo vệ dữ liệu, nhưng chỉ trả database exception. API tốt hơn cần replay order gốc hoặc báo “key reused with different payload”.

## Phần 2 — Serialize cùng key, sau đó replay

Xóa dữ liệu:

```sql
TRUNCATE orders RESTART IDENTITY;
```

Trong A, giữ transaction mở trước khi insert:

```sql
BEGIN;
SELECT pg_advisory_xact_lock(hashtextextended(
  '11111111-1111-1111-1111-111111111111:checkout-2026-0001', 0));
SELECT * FROM orders
WHERE customer_id = '11111111-1111-1111-1111-111111111111'
  AND idempotency_key = 'checkout-2026-0001'; -- không có row
INSERT INTO orders (customer_id, product_id, quantity, idempotency_key)
VALUES ('11111111-1111-1111-1111-111111111111', 10, 1, 'checkout-2026-0001');
-- ĐỪNG commit ngay: mở B trước.
```

Trong B:

```sql
BEGIN;
SELECT pg_advisory_xact_lock(hashtextextended(
  '11111111-1111-1111-1111-111111111111:checkout-2026-0001', 0));
-- lệnh này chờ A commit vì cùng logical key
```

Quay A chạy `COMMIT;`. B tiếp tục, rồi chạy:

```sql
SELECT id, product_id, quantity FROM orders
WHERE customer_id = '11111111-1111-1111-1111-111111111111'
  AND idempotency_key = 'checkout-2026-0001';
COMMIT;
```

B thấy order A tạo. API phải trả response từ order này, **không** decrement inventory/tạo order lần nữa.

## Phần 3 — Cùng key, payload khác là client bug/conflict

Sử dụng row đã tồn tại, giả sử B gửi product `99` hoặc quantity `2`. Sau khi lock và select row, application phải so sánh `product_id` và `quantity` với command. Khác nhau thì trả conflict `idempotency-key-reused`; không overwrite row cũ và không tạo row mới.

## Cần giải thích lại

1. Tại sao key phải scope theo customer, không dùng key global?
2. Vì sao unique constraint vẫn cần dù đã có advisory lock?
3. Khi response mạng bị mất sau server commit, retry nhận status/body gì?
4. Idempotency có rollback side effect khi transaction thất bại không?
5. Vì sao idempotency key không thay authentication/authorization?

## Liên hệ code production

- [`PlaceOrder`](../../backend/src/main/java/com/ngoctri/flashsale/order/application/PlaceOrder.java): lock → existing order → payload check → replay hoặc side effects.
- [`JdbcOrderPlacementStore`](../../backend/src/main/java/com/ngoctri/flashsale/order/infrastructure/persistence/JdbcOrderPlacementStore.java): PostgreSQL advisory transaction lock và query lookup.
- [`V3 migration`](../../backend/src/main/resources/db/migration/V3__create_orders.sql): unique constraint cùng customer/key.
- [`OrderApiIntegrationTest`](../../backend/src/test/java/com/ngoctri/flashsale/order/api/OrderApiIntegrationTest.java): tìm test header validation, replay và key reuse.

## Dọn môi trường

```powershell
docker compose down -v
```
