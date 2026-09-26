# Playbook: Redis trong Java/Spring ở hệ thống lớn

Đọc [Redis trong Spring Boot cho người mới](redis_spring_boot_beginner_guide.md) trước để hiểu cache/rate-limit/lock bằng flow; file này đi thẳng vào Java implementation và evidence.

## Chọn pattern theo failure

| Mục tiêu | Pattern | Evidence |
|---|---|---|
| Cache read | cache-aside, TTL+jitter, invalidate sau write | hit rate, stale rate, miss latency |
| Rate limit | Lua atomic window/token bucket | accepted ≤ limit dưới concurrent load |
| Distributed lock | random token + `SET NX PX` + compare/delete Lua | owner khác không unlock được |

Redis không phải source of truth cho Inventory/Order. Redis restart, eviction, replication lag và network partition phải có behavior rõ.

## Custom Java code

```java
var acquired = Boolean.TRUE.equals(redis.opsForValue()
    .setIfAbsent(key, token, Duration.ofSeconds(5)));
```

Release dùng `RedisScript` compare token rồi delete trong một lần execute; không viết `GET` rồi `DEL` ở hai round trip. Rate limit cũng dùng Lua để increment + expiry + decision atomic.

## Lab

Chạy [Redis lab](../../playbooks/08-redis-production-lab/README.md), sau đó tạo một Spring Boot scratch project với `StringRedisTemplate`:

1. Cache Product detail, thêm TTL jitter.
2. Viết Lua rate limit 5 request/60s.
3. Chạy 100 task đồng thời và assert accepted ≤ 5.
4. Kill process giữ lock; chờ TTL; chạy owner khác.
5. Đổi DB price rồi quan sát stale cache; thêm invalidate/test.

## Interview

“Tôi dùng Redis để giảm đọc, giới hạn tốc độ hoặc phối hợp ngắn hạn. Cache có thể stale/rebuild; rate limit cần script atomic; lock cần token ownership và TTL. Nếu invariant đã nằm trong PostgreSQL, Redis lock chỉ thêm coordination và failure mode.”
