# Lab 05 — Java race condition và virtual threads

Yêu cầu Java 21. Không cần Maven, Spring hay Docker.

```powershell
cd playbooks/05-java-concurrency-lab
New-Item -ItemType Directory -Force out | Out-Null
javac --release 21 -d out src/main/java/com/ngoctri/playbook/RaceConditionLab.java
java -cp out com.ngoctri.playbook.RaceConditionLab
```

`Unsafe count` thường nhỏ hơn `1,000,000`; kết quả có thể khác mỗi lần chạy vì thread scheduling không deterministic. `Synchronized` và `AtomicInteger` phải ra đúng `1,000,000`.

## Điều xảy ra trong `value++`

`value++` không phải một operation atomic. Hãy coi nó là:

```text
read value → add 1 → write value
```

Hai virtual thread có thể cùng read `42`, cùng write `43`: một increment bị mất. `CountDownLatch` làm 100 task bắt đầu gần cùng thời điểm để race dễ xuất hiện.

## Thử nghiệm bắt buộc

1. Chạy 5 lần, ghi unsafe result.
2. Đổi `TASKS` thành 1: vì sao unsafe lại đúng?
3. Đổi virtual thread executor thành `Executors.newFixedThreadPool(8)`: correctness thay đổi không, throughput/scheduling có thể thay đổi gì?
4. Bỏ `start.await()`: race biến mất hoàn toàn không, tại sao không nên kết luận từ một lần run?
5. Thay `AtomicInteger` bằng `volatile int`: giải thích tại sao vẫn sai. `volatile` tạo visibility, không biến read-modify-write thành atomic.

## Liên hệ backend nhiều replica

`synchronized`/`AtomicInteger` chỉ bảo vệ memory trong **một JVM**. Nếu Pod A và Pod B đều có `stock = 1` trong memory, mỗi pod vẫn có thể accept một order. Inventory source of truth của project là PostgreSQL, nên anti-oversell phải dùng conditional atomic SQL hoặc database lock. Xem [Playbook 01](../../docs/visual-guides/atomic_order_inventory_playbook.md) và [Playbook 04](../../docs/visual-guides/inventory_locking_strategies_playbook.md).
