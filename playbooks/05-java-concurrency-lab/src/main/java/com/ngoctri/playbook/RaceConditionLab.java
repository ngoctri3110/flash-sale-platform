package com.ngoctri.playbook;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Run with Java 21; no framework or dependency required. */
public final class RaceConditionLab {
    private static final int TASKS = 100;
    private static final int INCREMENTS_PER_TASK = 10_000;

    private RaceConditionLab() {}

    public static void main(String[] args) throws Exception {
        var expected = TASKS * INCREMENTS_PER_TASK;
        System.out.println("Expected count: " + expected);
        System.out.println("Unsafe count:   " + runUnsafe());
        System.out.println("Synchronized:   " + runSynchronized());
        System.out.println("AtomicInteger:  " + runAtomic());
    }

    private static int runUnsafe() throws Exception {
        var counter = new UnsafeCounter();
        runConcurrently(counter::increment);
        return counter.value;
    }

    private static int runSynchronized() throws Exception {
        var counter = new SynchronizedCounter();
        runConcurrently(counter::increment);
        return counter.value();
    }

    private static int runAtomic() throws Exception {
        var counter = new AtomicInteger();
        runConcurrently(counter::incrementAndGet);
        return counter.get();
    }

    private static void runConcurrently(Runnable increment) throws Exception {
        var ready = new CountDownLatch(TASKS);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (var task = 0; task < TASKS; task++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    for (var incrementNumber = 0; incrementNumber < INCREMENTS_PER_TASK; incrementNumber++) {
                        increment.run();
                    }
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (var future : futures) {
                future.get();
            }
        }
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
