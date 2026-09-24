# Lab 08 — Redis cache, rate limit và lock token

## Chạy

```powershell
cd playbooks/08-redis-production-lab
docker compose up -d
docker compose exec redis redis-cli
```

## Cache-aside

```redis
SET product:1 "{\"name\":\"Keyboard\",\"price\":100000}" EX 60
GET product:1
TTL product:1
```

Đổi giá ở database không tự đổi cache; hãy quyết định invalidate sau write hoặc chấp nhận stale TTL. Không cache inventory availability như source of truth.

## Rate limit atomic

Thử 5 lần:

```redis
INCR rate:user:demo:minute
EXPIRE rate:user:demo:minute 60 NX
GET rate:user:demo:minute
```

Sau đó giải thích vì sao hai lệnh rời vẫn có race khi nhiều pod. Bước tiếp theo là gom `INCR + EXPIRE + limit check` vào Lua script, rồi viết integration test 100 concurrent attempts với expected accepted <= limit.

## Lock có ownership

```redis
SET lock:demo token-a NX PX 5000
SET lock:demo token-b NX PX 5000
EVAL "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end" 1 lock:demo token-b
EVAL "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end" 1 lock:demo token-a
```

Kết quả: token-b không được xóa lock của token-a; token-a được xóa. TTL là safety net khi process chết, không phải proof cho business transaction.

## Dọn

```powershell
docker compose down -v
```
