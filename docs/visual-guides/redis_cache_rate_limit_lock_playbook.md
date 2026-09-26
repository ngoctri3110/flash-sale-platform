# Playbook: Redis cache, rate limit và distributed lock

Nếu mới học Redis, đọc [Redis trong Spring Boot cho người mới](redis_spring_boot_beginner_guide.md) trước; file này là playbook ngắn để chạy lab và review pattern.

## Dùng Redis cho ba bài toán khác nhau

| Bài toán | Pattern | Nỗi đau nếu làm sai |
|---|---|---|
| Read cache | Cache-aside + TTL + jitter | stale price, stampede, memory đầy |
| Rate limit | Atomic counter/window bằng Lua | race giữa nhiều pod, reset sai |
| Coordination lock | token ownership + TTL + safe release | unlock nhầm lock của owner khác |

Redis không thay source of truth order/inventory. Lock Redis không làm transaction PostgreSQL và payment cùng atomic.

## Lab

Chạy [Redis lab](../../playbooks/08-redis-production-lab/README.md). Dùng 3 terminal: set/get cache, chạy rate limit cùng key, kill client giữ lock và quan sát TTL.

## Custom code cần hiểu

Rate limit phải là một Redis Lua script kiểm tra rồi increment trong cùng execution. Lock phải ghi random token:

```text
SET lock:order:{id} {random-token} NX PX 5000
```

Release chỉ được xóa khi value còn đúng token, cũng bằng Lua. Cache-aside: read cache → miss → load DB → set TTL; chống stampede bằng jitter/single-flight tùy mức cần thiết.

## Interview

“Tôi chọn Redis theo failure mode: cache có thể stale và rebuild; rate limit cần atomic script; lock cần ownership token và expiry. Tôi không dùng Redis lock để thay conditional update ở database nếu invariant đã nằm trong PostgreSQL.”
