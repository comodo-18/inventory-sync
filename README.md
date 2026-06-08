# InventorySync ⚡

A Spring Boot microservice implementing **Redisson distributed locking** for oversell prevention in a high-concurrency stock reservation system. Communicates with CatalogCache via Apache Kafka.

[![Live](https://img.shields.io/badge/Live-Render-46E3B7?style=flat-square&logo=render)](https://inventory-sync-8u4t.onrender.com/swagger-ui.html)
[![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=java)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)

---

## 🚀 Live Demo

**API Docs (Swagger):** https://inventory-sync-8u4t.onrender.com/swagger-ui.html

> Note: Free tier — first request may take 50+ seconds to wake up.

---

## 🏗️ Architecture

```
CatalogCache → Kafka → InventorySync
  (producer)          (consumer)
                         ↓
                 Redisson Lock (Redis)
                         ↓
                   PostgreSQL DB
                   (inventory_items)
```

---

## ✨ Key Features

- **Redisson distributed locking** — per-product lock keys prevent oversell across all service instances
- **Kafka consumer** — reacts to `product-cache-invalidation` events from CatalogCache
- **Race condition tested** — 10-thread concurrency test proves zero oversell
- **Database-per-Service** — owns its own `inventory_items` table, independent of CatalogCache
- **Stateless design** — any instance handles any request, supports horizontal scaling

---

## 🔑 Trade-off Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Locking | Redisson (Redis) | `synchronized` only works within one JVM — multi-instance needs distributed lock |
| Lock scope | Per-product key | Finer granularity — product A's lock doesn't block product B |
| Delivery | At-least-once | Idempotent consumers — processing same event twice gives same result |
| DB | PostgreSQL (Supabase) | Shared infrastructure, separate tables (Database-per-Service pattern) |

---

## 🔒 Oversell Prevention — How It Works

```
User A and User B both try to buy last unit (stock = 1):

User A → acquires "lock:inventory:1" in Redis ✅
User B → tries "lock:inventory:1" → BLOCKED

User A → reads stock = 1 → decrements → stock = 0 → saves → releases lock
User B → acquires lock → reads stock = 0 → REJECTS reservation ✅

Result: zero oversell, exactly 1 unit sold
```

Why not Java `synchronized`?
> `synchronized` only locks within ONE JVM. With 10 InventorySync instances on Way Day, each has its own lock — they don't see each other. Redisson stores the lock in Redis — shared across ALL instances.

---

## 📡 API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/inventory/seed?productId={id}&stock={n}` | Seed a product with stock |
| `POST` | `/api/inventory/reserve?productId={id}&quantity={n}` | Reserve stock (uses distributed lock) |

---

## 🧪 Race Condition Test

```java
// StockReservationConcurrencyTest
// 10 threads, stock = 5, concurrent reservations

// Result:
// Exactly 5 threads succeed ✅
// Exactly 5 threads fail (insufficient stock) ✅
// Final stock = 0 ✅
// Zero oversell proven ✅
```

```bash
./mvnw test -Dtest=StockReservationConcurrencyTest
```

---

## 📨 Kafka Integration

Consumes from topic: `product-cache-invalidation`

```
Event: { productId: 1, eventType: "UPDATED" }
       → marks product as NEEDS_RECHECK

Event: { productId: 1, eventType: "DELETED" }
       → marks product as DISCONTINUED
```

> Kafka is disabled in production (`kafka.enabled=false`) as no free Kafka broker is available on Render free tier. Upstash Kafka integration planned.

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|------------|
| Framework | Spring Boot 3.5, Java 21 |
| Distributed Lock | Redisson 3.27 (Redis-backed) |
| Cache/Lock Store | Redis (Upstash) |
| Database | PostgreSQL (Supabase Session Pooler) |
| Messaging | Apache Kafka (local), Upstash Kafka (planned) |
| Deployment | Render (Docker with mvnw wrapper) |

---

## ⚙️ Running Locally

**Prerequisites:** Java 21, Docker, running CatalogCache

```bash
# Clone the repo
git clone https://github.com/comodo-18/inventory-sync.git
cd inventory-sync

# Run the app (port 8081)
./mvnw spring-boot:run
```

App runs on `http://localhost:8081`

**Test the distributed lock:**

```bash
# Seed a product with 5 units
curl -X POST "http://localhost:8081/api/inventory/seed?productId=1&stock=5"

# Reserve units (run 6 times — 5 succeed, 6th fails)
curl -X POST "http://localhost:8081/api/inventory/reserve?productId=1&quantity=1"
```

---

## 🔧 Environment Variables (Production)

```
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:postgresql://<supabase-pooler-host>:5432/postgres
DB_USER=<supabase-user>
DB_PASSWORD=<password>
REDIS_HOST=<upstash-host>
REDIS_PORT=6379
REDIS_PASSWORD=<upstash-password>
PORT=10000
```

---

## 🔗 Related Projects

- [**CatalogCache**](https://github.com/comodo-18/catalogue-cache) — Redis caching + Kafka producer for cache invalidation events