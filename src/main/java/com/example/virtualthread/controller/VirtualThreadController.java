package com.example.virtualthread.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@RestController
@RequestMapping("/api")
public class VirtualThreadController {

    // Shared fixed thread pool (only 10 threads for all incoming requests)
    private static final ExecutorService PLATFORM_POOL = Executors.newFixedThreadPool(10);

    /**
     * WITHOUT virtual threads — uses a shared fixed pool of 10 platform threads.
     *
     * With 20 concurrent users:
     *   - 10 requests start immediately
     *   - 10 requests WAIT in queue until a thread is free
     *
     * 'start' is captured at request entry (BEFORE queuing),
     * so totalTime = queue wait time + 200ms execution time.
     * Queued requests will show ~400ms+ in both logs and JMeter.
     */
    @GetMapping("/without-virtual-thread")
    public String withoutVirtualThread() throws Exception {
        // Captured HERE — before the task is queued
        long start = System.currentTimeMillis();

        Future<String> future = PLATFORM_POOL.submit(() -> {
            Thread t = Thread.currentThread();
            long queueWait = System.currentTimeMillis() - start; // time spent waiting for a thread

            // Simulate I/O-bound work (e.g., DB call, network call)
            Thread.sleep(200);

            long totalTime = System.currentTimeMillis() - start;
            IO.println("[PLATFORM] virtual=" + t.isVirtual()
                + " | queueWait=" + queueWait + "ms"
                + " | totalTime=" + totalTime + "ms"
                + " | thread=" + t.getName());

            return String.format(
                "WITHOUT Virtual Thread | thread=%s | virtual=%s | queueWait=%dms | totalTime=%dms",
                t.getName(), t.isVirtual(), queueWait, totalTime
            );
        });
        return future.get();
    }

    /**
     * WITH virtual threads — creates a new virtual thread per task instantly.
     *
     * With 20 concurrent users:
     *   - ALL 20 requests get a virtual thread immediately (no queuing)
     *   - All complete in ~200ms
     *
     * 'start' is captured at request entry; queueWait should be ~0ms.
     */
    @GetMapping("/with-virtual-thread")
    public String withVirtualThread() throws Exception {
        // Captured HERE — before the task is submitted
        long start = System.currentTimeMillis();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> future = executor.submit(() -> {
                Thread t = Thread.currentThread();
                long queueWait = System.currentTimeMillis() - start; // should be ~0ms

                // Simulate I/O-bound work (e.g., DB call, network call)
                Thread.sleep(200);

                long totalTime = System.currentTimeMillis() - start;
                IO.println("[VIRTUAL ] virtual=" + t.isVirtual()
                    + " | queueWait=" + queueWait + "ms"
                    + " | totalTime=" + totalTime + "ms"
                    + " | thread=" + t.getName());

                return String.format(
                    "WITH Virtual Thread | thread=%s | virtual=%s | queueWait=%dms | totalTime=%dms",
                    t.getName(), t.isVirtual(), queueWait, totalTime
                );
            });
            return future.get();
        }
    }
}
