package com.ngoctri.playbook;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Run with Java 21; no framework or dependency required. */
public final class RaceConditionLab {
    private static final int TASKS = 100;
    private static final int INCREMENTS_PER_TASK = 10_000;

    private RaceConditionLab() {}

    public static void main(String[] args) throws Exception {
        var executorKind = args.length == 0 ? "virtual" : args[0];
        var expected = TASKS * INCREMENTS_PER_TASK;
        System.out.println("Executor:        " + executorKind);
        System.out.println("Expected count: " + expected);
        System.out.println("Unsafe count:   " + runUnsafe(executorKind));
        System.out.println("Synchronized:   " + runSynchronized(executorKind));
        System.out.println("AtomicInteger:  " + runAtomic(executorKind));
    }

    private static int runUnsafe(String executorKind) throws Exception {
        var counter = new UnsafeCounter();
        runConcurrently(counter::increment, executorKind);
        return counter.value;
    }

    private static int runSynchronized(String executorKind) throws Exception {
        var counter = new SynchronizedCounter();
        runConcurrently(counter::increment, executorKind);
        return counter.value();
    }

    private static int runAtomic(String executorKind) throws Exception {
        var counter = new AtomicInteger();
        runConcurrently(counter::incrementAndGet, executorKind);
        return counter.get();
    }

    private static void runConcurrently(Runnable increment, String executorKind) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = newExecutor(executorKind)) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (var task = 0; task < TASKS; task++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (var incrementNumber = 0; incrementNumber < INCREMENTS_PER_TASK; incrementNumber++) {
                        increment.run();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }
    }

    private static ExecutorService newExecutor(String executorKind) {
        return switch (executorKind) {
            case "fixed" -> Executors.newFixedThreadPool(8);
            case "virtual" -> Executors.newVirtualThreadPerTaskExecutor();
            default -> throw new IllegalArgumentException("Use executor: virtual or fixed");
        };
    }

    private static final class UnsafeCounter {
        private int value;
        private void increment() { value++; }
    }

    private static final class SynchronizedCounter {
        private int value;
        private synchronized void increment() { value++; }
        private synchronized int value() { return value; }
    }
}
