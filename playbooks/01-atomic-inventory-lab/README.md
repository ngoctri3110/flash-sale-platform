# Lab 01 — Atomic Inventory Decrement

Mục tiêu không phải thuộc câu SQL. Bạn sẽ tự tạo ra **oversell** với cách sai, sau đó chứng minh cách đúng chỉ nhận đúng một order khi stock = 1.

## Chuẩn bị

```powershell
cd playbooks/01-atomic-inventory-lab
docker compose up -d
docker compose exec postgres psql -U lab -d inventory_lab
```

Mở **hai** PowerShell khác nhau, chạy cùng lệnh cuối ở mỗi cửa sổ. Gọi chúng là Terminal A và Terminal B. Dùng `\i /path/file.sql` không tiện trên Windows container, nên copy từng SQL block dưới đây vào `psql`.

## Phần 1 — Tạo lỗi read-then-write

Reset trước mỗi lần thử:

```sql
TRUNCATE orders RESTART IDENTITY;
UPDATE inventories SET available_quantity = 1 WHERE product_id = 1;
```

Trong **A**, chạy hai câu đầu rồi dừng trước `INSERT`:

```sql
BEGIN;
SELECT available_quantity FROM inventories WHERE product_id = 1; -- thấy 1
-- Application code kết luận: "còn hàng"
```

Trong **B**, chạy và commit hết:

```sql
BEGIN;
SELECT available_quantity FROM inventories WHERE product_id = 1; -- cũng thấy 1
INSERT INTO orders (product_id, quantity) VALUES (1, 1);
UPDATE inventories SET available_quantity = 0 WHERE product_id = 1;
COMMIT;
```

Quay lại **A**. Application đã quyết định accept từ lúc đọc stock = 1, nên tiếp tục:

```sql
INSERT INTO orders (product_id, quantity) VALUES (1, 1);
UPDATE inventories SET available_quantity = 0 WHERE product_id = 1;
COMMIT;
```

Kiểm tra ở một terminal:

```sql
SELECT product_id, available_quantity FROM inventories;
SELECT COUNT(*) AS accepted_orders, COALESCE(SUM(quantity), 0) AS accepted_quantity FROM orders;
```

Kết quả: `available_quantity = 0` nhưng `accepted_quantity = 2`. Database check constraint không thấy số âm, nhưng business invariant đã vỡ: bạn bán 2 món khi chỉ có 1 món. Đây là lý do lỗi có thể rất khó phát hiện nếu chỉ kiểm tra stock âm.

## Phần 2 — Sửa bằng conditional atomic UPDATE

Reset lại. Trong **A**, chạy và giữ transaction mở sau câu `UPDATE`:

```sql
BEGIN;
UPDATE inventories
SET available_quantity = available_quantity - 1
WHERE product_id = 1
  AND available_quantity >= 1;
-- psql phải báo UPDATE 1. Khi đó mới được INSERT order.
INSERT INTO orders (product_id, quantity) VALUES (1, 1);
```

Trong **B**, chỉ chạy phần dưới đây. Không copy câu `INSERT` của A:

```sql
BEGIN;
UPDATE inventories
SET available_quantity = available_quantity - 1
WHERE product_id = 1
  AND available_quantity >= 1;
```

B sẽ đợi row lock của A. Quay lại **A** và commit:

```sql
COMMIT;
```

Quay lại **B**. PostgreSQL re-check predicate sau khi lock được nhả; B phải báo `UPDATE 0`. Khi đó **không được INSERT order** và chạy:

```sql
ROLLBACK;
```

Chạy verify. Kết quả đúng: stock 0, accepted quantity 1, order count 1.

## Điều phải giải thích lại không nhìn tài liệu

1. Vì sao `@Transactional` không tự sửa được cách read-then-write?
2. Affected row count `0` đại diện cho business outcome gì?
3. Tại sao phải chỉ `INSERT order` sau khi conditional update thành công?
4. Vì sao `synchronized` trong Java không bảo vệ được hai pod backend?
5. Nếu một order chứa nhiều SKU, atomic statement một row còn đủ không?

## Liên hệ implementation production

Sau khi xong lab, mới xem:

- [`PlaceOrder`](../../backend/src/main/java/com/ngoctri/flashsale/order/application/PlaceOrder.java): transaction và business flow.
- [`JdbcOrderPlacementStore`](../../backend/src/main/java/com/ngoctri/flashsale/order/infrastructure/persistence/JdbcOrderPlacementStore.java): atomic SQL thật, kết quả update được đổi thành boolean.
- [`InventoryConcurrencyStrategyLabIntegrationTest`](../../backend/src/test/java/com/ngoctri/flashsale/inventory/infrastructure/persistence/InventoryConcurrencyStrategyLabIntegrationTest.java): so sánh atomic/pessimistic/optimistic bằng integration test.

## Dọn môi trường

```powershell
docker compose down -v
```
