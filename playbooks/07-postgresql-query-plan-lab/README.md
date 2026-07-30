# Lab 07 — PostgreSQL index, pagination và EXPLAIN ANALYZE

Mục tiêu: chọn index từ query thật, không từ “cột này hay filter nên cứ index”. Lab seed 200,000 orders, đủ để planner cho thấy khác biệt.

## Chạy lab

```powershell
cd playbooks/07-postgresql-query-plan-lab
docker compose up -d
docker compose exec postgres psql -U lab -d query_plan_lab
```

## Phần 1 — đo trước khi tạo index

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, product_id, total_amount, created_at
FROM orders
WHERE product_id = 42
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

Quan sát: planner thường đọc rất nhiều rows (Seq Scan), lọc `product_id`, rồi Sort trước khi lấy 20 rows. Ghi `Execution Time`, `Buffers`, node scan và sort.

## Phần 2 — index theo filter + sort

```sql
CREATE INDEX ix_orders_product_created
    ON orders (product_id, created_at DESC, id DESC);
ANALYZE orders;

EXPLAIN (ANALYZE, BUFFERS)
SELECT id, product_id, total_amount, created_at
FROM orders
WHERE product_id = 42
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

Index này khớp query shape: equality filter `product_id`, sau đó sort `created_at DESC, id DESC`. Planner có thể đi thẳng đúng phần index và dừng sau 20 rows, thay vì sort toàn bộ candidate rows.

## Phần 3 — offset vs keyset pagination

```sql
-- Offset: database vẫn phải bỏ qua các row trước đó.
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, product_id, total_amount, created_at
FROM orders
WHERE product_id = 42
ORDER BY created_at DESC, id DESC
LIMIT 20 OFFSET 1000;
```

Lấy row cuối page trước, ví dụ timestamp `2026-06-01 10:00:00+00`, id `123`. Sau đó dùng cursor/keyset:

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, product_id, total_amount, created_at
FROM orders
WHERE product_id = 42
  AND (created_at, id) < (TIMESTAMPTZ '2026-06-01 10:00:00+00', 123)
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

Cursor values phải lấy từ page trước, không hard-code trong API. So sánh đúng trên page sâu. Offset đơn giản và tốt cho admin page nông; keyset tốt hơn cho scroll/feed lớn, đổi lại client phải mang opaque cursor và không nhảy thẳng page 500 dễ dàng.

## Câu hỏi mentor

1. Tại sao index `(created_at, id, product_id)` không khớp bằng `(product_id, created_at, id)` cho query này?
2. Tại sao `SELECT *` có thể không index-only scan dù index đã đúng filter/order?
3. Mỗi index làm INSERT order phải trả thêm chi phí gì?
4. Vì sao chỉ thấy Index Scan không tự động là performance tốt? Hãy nhìn actual time/rows/buffers.
5. Nếu API filter theo `customer_id` thay vì `product_id`, index hiện tại dùng được đến đâu?

## Dọn lab

```powershell
docker compose down -v
```
