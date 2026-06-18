# Architectural Audit & Stack Verification Checklist

Before writing the first line of code, this document acts as a critical audit of our tech stack, modular isolation, scaling strategies, and B2B white-label packaging model.

---

## 1. Stack Suitability & Cost Optimization (Crucial for Startups)

Our currently planned stack:
`Java 25 (Loom) + Spring Boot 4 + PostgreSQL 17 + Redis 8 + MinIO + Kafka + Elasticsearch 9 + pgvector`

### The Architect's Critique:
Running **Kafka** and **Elasticsearch** in addition to Postgres, Redis, and MinIO creates a **massive infrastructure footprint** (high RAM/CPU costs). If we sell a "chunk" of our product (e.g., just PDF storefronts) to a small client, requiring them to host Kafka and Elasticsearch is cost-prohibitive.

### Recommended Adjustments:

```
                  ┌────────────────────────────────────────┐
                  │          PostgreSQL 17 Database        │
                  │  - Transactional Data Storage          │
                  │  - pgvector (Semantic AI Search)       │
                  │  - Full-Text Search (Bangla & English) │
                  └────────────────────────────────────────┘
                                      │
              ┌───────────────────────┴───────────────────────┐
              ▼                                               ▼
   [Option A: Standalone Chunk]                   [Option B: Enterprise Scale]
   - Messaging: DB Event Registry                 - Messaging: Apache Kafka
   - Search: Postgres Full-Text                   - Search: Elasticsearch 9
   - Low infra cost, single DB                    - High throughput, split load
```

* **Replace Elasticsearch with Postgres FTS**: For the MVP and lightweight client installs, use PostgreSQL's built-in Full-Text Search (FTS) with custom Bangla dictionary configurations. Swap in Elasticsearch only for high-tier enterprise clients.
* **Replace Kafka with DB Event Publication**: Use Spring Modulith's database-backed **Event Publication Registry** for async communication by default. It runs inside the transactional DB database, requiring zero external infrastructure. Deploy Kafka only when scaling to high-throughput message streaming.
* **Result**: The system runs on a **minimal footprint** (Postgres + Redis + MinIO), making the "chunks" extremely cheap to host for small clients.

---

## 2. Ensuring "Sellable in Chunks" Decoupling

To guarantee a package can be deleted or disabled without breaking compilation, we must set up these guardrails:

### A. Stub/Mock Implementation Fallbacks
If Module A calls an interface in Module B, and Module B is physically deleted/excluded from the build:
1. Define all module interfaces inside the `api/contract/` package.
2. Put stubs or default implementations in a `shared/stubs/` package.
3. Annotate the actual service implementations with `@Primary` or use Spring's `@ConditionalOnMissingBean` on the stubs.
4. *Result*: If the actual module is excluded from compilation, Spring will automatically instantiate the stub bean, allowing the calling module to function normally.

### B. Isolated Database Migrations (Flyway per Module)
* **The Problem**: A single, shared Flyway folder (`db/migration/`) will fail if database tables for excluded modules are missing.
* **The Solution**: Segment migration scripts by module prefix:
  - `src/main/resources/db/migration/identity/...`
  - `src/main/resources/db/migration/billing/...`
* Configure Flyway to dynamically scan and execute migrations *only* for the modules present in the classpath.

---

## 3. High Concurrency & Virtual Thread Tuning

Using Java 25 Virtual Threads (Loom) changes how we handle resource pools:

* **HikariCP Connection Pool Pinning**:
  - Virtual threads are cheap (you can run millions), but database connections are scarce. If a database transaction blocks or runs slowly, virtual threads will saturate and exhaust the pool.
  - *Rule*: We must never execute HTTP client calls, payment gateway redirects, or SMS OTP calls inside a database `@Transactional` block. Transactions must be extremely short.
* **Avoiding synchronized Blocks**:
  - In Java, using the `synchronized` keyword inside a virtual thread can "pin" the thread to its underlying OS carrier thread, breaking scalability.
  - *Rule*: Use `ReentrantLock` or Redis-based distributed locks (via Redisson) instead of `synchronized` for concurrency control.

---

## 4. White-Label Storefront Customization (Tenancy Metadata)

To support custom branding and white-label domains for B2B publishers:
* The `tenant` module must define a `tenant_metadata` table containing: custom stylesheets, brand logos, color themes, custom domain bindings, and payment credentials.
* **Domain Routing**: Implement a Spring MVC Interceptor that checks the incoming HTTP request host header (`X-Forwarded-Host` or `Host`), extracts the tenant ID, and sets it on the thread-local `TenantContext` before the controller executes.
