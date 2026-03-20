# Virtual Thread Performance Demo

A **Spring Boot** application that demonstrates and benchmarks the performance difference between **Platform Threads** (traditional fixed thread pool) and **Java Virtual Threads** (Project Loom) under concurrent load.

---

## 📋 Table of Contents

- [Overview](#overview)
- [What Are Virtual Threads?](#what-are-virtual-threads)
- [Platform Threads vs Virtual Threads](#platform-threads-vs-virtual-threads)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [API Endpoints](#api-endpoints)
- [How It Works](#how-it-works)
- [Running the Application](#running-the-application)
- [Performance Benchmarks](#performance-benchmarks)
- [JMeter Load Test Results](#jmeter-load-test-results)
- [Key Takeaways](#key-takeaways)

---

## Overview

This project provides a side-by-side comparison of two REST endpoints:

| Endpoint | Thread Model | Behavior under 100 concurrent users |
|---|---|---|
| `/api/without-virtual-thread` | Fixed pool of **10 platform threads** | First 10 proceed; remaining 90 **queue and wait** in batches |
| `/api/with-virtual-thread` | **Virtual thread per task** | All 100 get a thread **instantly**, no queuing |

Each endpoint simulates **200ms of I/O-bound work** (e.g., a database call or network request) and returns metrics including `queueWait` and `totalTime`.

---

## What Are Virtual Threads?

Virtual Threads were introduced as a **preview feature in Java 19** and became **stable in Java 21** (JEP 444) as part of Project Loom.

- **Platform Threads** are thin wrappers around OS threads. They are **expensive** to create and limited in number (typically thousands at most).
- **Virtual Threads** are **lightweight threads managed by the JVM**, not the OS. They can be created in **millions** with minimal overhead.

> 💡 Virtual Threads are ideal for **I/O-bound** workloads (DB queries, REST calls, file reads) where threads spend most of their time waiting — not for CPU-intensive tasks.

---

## Platform Threads vs Virtual Threads

| Feature | Platform Thread | Virtual Thread |
|---|---|---|
| Managed by | Operating System | JVM |
| Creation cost | High (~1MB stack per thread) | Very low (a few KB) |
| Max concurrent threads | Thousands | Millions |
| Blocking behaviour | Blocks OS thread | Unmounts from carrier thread (non-blocking) |
| Best use case | CPU-bound tasks | I/O-bound tasks |
| Thread name | `pool-N-thread-M` | `` (anonymous) |
| `Thread.isVirtual()` | `false` | `true` |

---

## Tech Stack

| Technology | Version |
|---|---|
| Java | 25 |
| Spring Boot | 4.0.3 |
| Spring Web MVC | (included) |
| Lombok | (included) |
| Build Tool | Maven |
| Load Testing | Apache JMeter |

---

## Project Structure

```
demo/
├── src/
│   └── main/
│       ├── java/com/example/virtualthread/
│       │   ├── VirtualThreadApplication.java       # Spring Boot entry point
│       │   └── controller/
│       │       └── VirtualThreadController.java    # REST API with both thread demos
│       └── resources/
│           └── application.yaml                    # App configuration
├── Platform Thread Performance Report.txt          # Captured platform thread logs
├── Virtual Thread Performance Report.txt           # Captured virtual thread logs
├── pom.xml
└── README.md
```

---

## API Endpoints

### 1. Without Virtual Thread — Platform Thread Pool

```
GET /api/without-virtual-thread
```

- Uses a **shared fixed thread pool of 10 platform threads**.
- With 20 concurrent requests, 10 requests are served immediately and 10 **wait in a queue**.
- Queue wait time accumulates with each batch, increasing total response time.

**Sample Response:**
```
WITHOUT Virtual Thread | thread=pool-2-thread-3 | virtual=false | queueWait=104ms | totalTime=305ms
```

---

### 2. With Virtual Thread — Virtual Thread Per Task

```
GET /api/with-virtual-thread
```

- Creates a **new virtual thread for every request instantly**.
- All 20 concurrent requests start immediately — **zero queue wait**.
- All requests complete in approximately **200ms** (just the simulated I/O time).

**Sample Response:**
```
WITH Virtual Thread | thread= | virtual=true | queueWait=0ms | totalTime=202ms
```

---

## How It Works

### Platform Thread Endpoint

```java
private static final ExecutorService PLATFORM_POOL = Executors.newFixedThreadPool(10);

@GetMapping("/without-virtual-thread")
public String withoutVirtualThread() throws Exception {
    long start = System.currentTimeMillis();

    Future<String> future = PLATFORM_POOL.submit(() -> {
        long queueWait = System.currentTimeMillis() - start; // time spent waiting in queue
        Thread.sleep(200); // simulate I/O
        long totalTime = System.currentTimeMillis() - start;
        // ...
    });
    return future.get();
}
```

- `start` is captured **before** the task is submitted.
- If all 10 threads are busy, the task sits in a queue.
- `queueWait` reveals how long the task waited before it could even begin.

---

### Virtual Thread Endpoint

```java
@GetMapping("/with-virtual-thread")
public String withVirtualThread() throws Exception {
    long start = System.currentTimeMillis();

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        Future<String> future = executor.submit(() -> {
            long queueWait = System.currentTimeMillis() - start; // always ~0ms
            Thread.sleep(200); // simulate I/O
            long totalTime = System.currentTimeMillis() - start;
            // ...
        });
        return future.get();
    }
}
```

- A **brand-new virtual thread** is created immediately for every request.
- `queueWait` is always **0ms** — no waiting, no queuing.
- During `Thread.sleep(200)`, the virtual thread **unmounts** from its carrier thread, freeing it for other tasks.

---

## Running the Application

### Prerequisites

- Java 21+ (Java 25 recommended)
- Maven 3.8+

### Steps

```bash
# Clone or open the project
cd "Java Workspace/demo"

# Build the project
./mvnw clean install

# Run the application
./mvnw spring-boot:run
```

The server starts on `http://localhost:8080` by default.

### Quick Test (curl)

```bash
# Test without virtual threads
curl http://localhost:8080/api/without-virtual-thread

# Test with virtual threads
curl http://localhost:8080/api/with-virtual-thread
```

---

## Performance Benchmarks

Actual log data captured by running both endpoints under **100 concurrent requests**.

### Platform Thread (Fixed Pool of 10)

With 100 requests and only 10 threads, requests queue in batches of 10. Each batch waits ~200ms more than the previous:

| Batch | Requests | queueWait | totalTime |
|---|---|---|---|
| Batch 1 | 1 – 10 | ~0ms | ~202–217ms |
| Batch 2 | 11 – 20 | ~100–117ms | ~301–319ms |
| Batch 3 | 21 – 30 | ~205–220ms | ~408–423ms |
| Batch 4 | 31 – 40 | ~306–320ms | ~507–527ms |
| Batch 5 | 41 – 50 | ~406–424ms | ~610–640ms |
| Batch 6 | 51 – 60 | ~509–541ms | ~714–746ms |
| Batch 7 | 61 – 70 | ~618–645ms | ~819–847ms |
| Batch 8 | 71 – 80 | ~717–747ms | ~918–948ms |
| Batch 9 | 81 – 90 | ~817–847ms | ~1018–1048ms |
| Batch 10 | 91 – 100 | ~919–947ms | **~1122–1148ms** |

> ⚠️ **Response time grows with load** — the last batch of requests waited nearly **1 second** just in queue before execution even started. Total time exceeded **1.1 seconds** for a task that only takes 200ms.

---

### Virtual Thread (One Per Task)

All 100 requests processed with **zero queue wait** throughout:

| Batch | queueWait | totalTime |
|---|---|---|
| All requests | **0–1ms** | **200–213ms** |

> ✅ **Response time stays flat** — virtual threads scale horizontally to match the number of incoming requests without penalty.

---

## JMeter Load Test Results

Test configuration: **100 concurrent users**, simulating I/O-bound work of **200ms** per request.

### Without Virtual Threads (Platform Threads)

```
[PLATFORM] virtual=false | queueWait=0ms   | totalTime=205ms  ← Batch 1  (got thread immediately)
[PLATFORM] virtual=false | queueWait=0ms   | totalTime=211ms
[PLATFORM] virtual=false | queueWait=104ms | totalTime=305ms  ← Batch 2  (waited ~100ms in queue)
[PLATFORM] virtual=false | queueWait=116ms | totalTime=317ms
[PLATFORM] virtual=false | queueWait=209ms | totalTime=411ms  ← Batch 3  (waited ~200ms in queue)
[PLATFORM] virtual=false | queueWait=220ms | totalTime=423ms
[PLATFORM] virtual=false | queueWait=314ms | totalTime=516ms  ← Batch 4
[PLATFORM] virtual=false | queueWait=418ms | totalTime=621ms  ← Batch 5
[PLATFORM] virtual=false | queueWait=541ms | totalTime=746ms  ← Batch 6
[PLATFORM] virtual=false | queueWait=645ms | totalTime=847ms  ← Batch 7
[PLATFORM] virtual=false | queueWait=747ms | totalTime=948ms  ← Batch 8
[PLATFORM] virtual=false | queueWait=847ms | totalTime=1048ms ← Batch 9
[PLATFORM] virtual=false | queueWait=947ms | totalTime=1148ms ← Batch 10 (last batch, ~1 sec queue wait!)
```

The **90 requests beyond the first batch** all experience queuing. The final batch waits nearly **1 second** before even starting.

### With Virtual Threads

```
[VIRTUAL ] virtual=true | queueWait=0ms | totalTime=202ms
[VIRTUAL ] virtual=true | queueWait=0ms | totalTime=210ms
[VIRTUAL ] virtual=true | queueWait=1ms | totalTime=203ms
[VIRTUAL ] virtual=true | queueWait=0ms | totalTime=202ms
[VIRTUAL ] virtual=true | queueWait=0ms | totalTime=200ms
...  (all 100 requests look exactly like this)
[VIRTUAL ] virtual=true | queueWait=0ms | totalTime=216ms
```

All 100 requests complete in **~200–216ms** — the queue wait is always `0–1ms`, regardless of concurrent load.

### Summary Table (100 users, 200ms simulated I/O)

| Metric | Platform Threads | Virtual Threads |
|---|---|---|
| Min response time | ~202ms | ~200ms |
| Max response time | **~1148ms** | **~216ms** |
| Queue wait (first batch) | ~0ms | ~0ms |
| Queue wait (last batch) | **~947ms** | **~0ms** |
| Total threads used | 10 (shared, fixed) | 100 (one per request) |
| Scales with load? | ❌ No — latency grows linearly | ✅ Yes — latency stays flat |

> 📌 **Why does platform thread time reach ~1148ms?** With 100 users and only 10 threads, requests are batched into 10 groups of 10. Each group waits for the previous group to finish (~200ms). The 10th batch waits 9 × 200ms = ~900ms before starting, plus the 200ms task itself = **~1100ms+ total**.

> 📌 **Why do some JMeter response times look similar between the two?** The minimum times will always be ~200ms (the I/O delay), so the first few requests look the same. The difference is visible in **average and maximum response times** — virtual threads keep max time at ~216ms while platform threads push it past **1 second** at 100 users.

---

## Key Takeaways

1. **Virtual threads eliminate queue wait** for I/O-bound workloads.
2. **Platform threads are limited** by pool size — extra requests must queue, increasing latency.
3. **Response time with virtual threads stays constant** regardless of the number of concurrent requests (within JVM memory limits).
4. **Virtual threads are not faster for CPU work** — if your task is CPU-bound (e.g., image processing, encryption), platform threads or parallel streams are more appropriate.
5. **Real-world gains** are even more pronounced with actual database calls, REST API calls, or file I/O, where threads block for tens to hundreds of milliseconds.
6. **Thread name is empty for virtual threads** — they are anonymous, managed entirely by the JVM scheduler.

---

## When to Use Virtual Threads

| Use Case | Virtual Threads? |
|---|---|
| REST API calling external services | ✅ Yes |
| Database queries (JDBC) | ✅ Yes |
| File I/O | ✅ Yes |
| Message queue consumers | ✅ Yes |
| CPU-intensive computation | ❌ No |
| Parallel number crunching | ❌ No |

---

## References

- [JEP 444 — Virtual Threads (Java 21)](https://openjdk.org/jeps/444)
- [Project Loom](https://openjdk.org/projects/loom/)
- [Spring Boot Virtual Threads Support](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.virtual-threads)
- [Apache JMeter](https://jmeter.apache.org/)

