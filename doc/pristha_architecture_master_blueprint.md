# Pristha: Master Architectural Blueprint & Codebase Initialization

This document serves as the absolute source of truth for the development of the **Pristha** modular monolith backend. It contains the schema specifications, package dependencies, service interfaces, and initial configurations required to build a highly maintainable, white-labelable, and high-scale startup platform.

---

## 1. System Topology & Module Dependency Matrix

The application is built on **Spring Modulith** with seven isolated business domains and a global `shared` library. 

```
                               ┌─────────────────┐
                               │     shared      │ (OPEN)
                               └─────────────────┘
                                  ▲           ▲
            ┌─────────────────────┼───────────┴───────────┐
            │                     │                       │
     ┌──────┴──────┐       ┌──────┴──────┐         ┌──────┴──────┐
     │  identity   │ ◄───  │   tenant    │         │   billing   │
     └─────────────┘       └─────────────┘         └──────┬──────┘
            ▲                     ▲                       ▲
            │                     │                       │ (identity::api-contract)
            └───────────┬─────────┘                       │
                        │                                 │
                 ┌──────┴──────┐                          │
                 │   studio    │ ─────────────────────────┘
                 └──────┬──────┘
                        │
                        ▼ (ContentPublishedEvent)
                 ┌─────────────┐
                 │   catalog   │ ◄─── [Elasticsearch / Feed]
                 └──────┬──────┘
                        │
                        ▼ (catalog::api-contract)
                 ┌─────────────┐
                 │   reading   │
                 └──────┬──────┘
                        │ (reading::api-event)
                        ▼
                 ┌─────────────┐
                 │  analytics  │
                 └─────────────┘
```

### Dependency Rules:
1. **No Circular Dependencies**: Modulith tests will fail if any cyclic relationships occur.
2. **Named Interfaces**: Modules must *only* import subpackages explicitly exposed via `@org.springframework.modulith.NamedInterface`.
3. **Internal Privacy**: All implementations under `com.prishtha.mvp.[module].internal.*` must be package-private (no `public` modifier) to prevent leakage.

---

## 2. Database Schema Specifications (PostgreSQL 17)

Every table belongs to a dedicated module schema to facilitate future microservice extraction. Flyway migrations must be partitioned into module-specific resource folders.

```
mvp_database/
  ├── identity schema
  ├── tenant schema
  ├── studio schema
  ├── catalog schema
  ├── reading schema
  ├── billing schema
  └── analytics schema
```

### A. Schema: `identity`
Tracks users, authentication states, and author profile activations.

```sql
-- Migration: V1__init_identity.sql
CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE identity.users (
    id BIGSERIAL PRIMARY KEY,
    phone VARCHAR(20) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE identity.author_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES identity.users(id),
    pen_name VARCHAR(100) NOT NULL UNIQUE,
    biography TEXT,
    payout_mfs_number VARCHAR(20) NOT NULL,
    payout_mfs_provider VARCHAR(20) NOT NULL, -- 'BKASH', 'NAGAD', 'ROCKET'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_users_phone ON identity.users(phone);
```

### B. Schema: `tenant`
Manages B2B publisher metadata and white-label storefront settings.

```sql
-- Migration: V1__init_tenant.sql
CREATE SCHEMA IF NOT EXISTS tenant;

CREATE TABLE tenant.tenants (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE tenant.tenant_domains (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant.tenants(id),
    custom_domain VARCHAR(255) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE tenant.tenant_themes (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL UNIQUE REFERENCES tenant.tenants(id),
    brand_logo_url VARCHAR(512),
    primary_color VARCHAR(10) DEFAULT '#000000',
    secondary_color VARCHAR(10) DEFAULT '#FFFFFF',
    custom_stylesheet_url VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
```

### C. Schema: `studio`
Stores all raw content drafts, reviewer invitations, and text edits.

```sql
-- Migration: V1__init_studio.sql
CREATE SCHEMA IF NOT EXISTS studio;

CREATE TABLE studio.categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE studio.writings (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL, -- Content owner (Author personal tenant or B2B publisher)
    author_id BIGINT NOT NULL REFERENCES identity.author_profiles(id),
    parent_id BIGINT REFERENCES studio.writings(id), -- Null for Books/Posts, points to Book for Chapters
    title VARCHAR(255), -- Optional for quick thoughts/posts
    body_json JSONB, -- Stores complex EditorJS or TipTap rich text payloads
    type VARCHAR(30) NOT NULL, -- 'BOOK', 'CHAPTER', 'POST', 'ARTICLE'
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT', -- 'DRAFT', 'UNFINISHED_PREVIEW', 'PUBLISHED', 'UNDER_REVIEW'
    price_type VARCHAR(20) NOT NULL DEFAULT 'FREE', -- 'FREE', 'LOCKED'
    price_amount DECIMAL(10,2) DEFAULT 0.00,
    order_index INT DEFAULT 0, -- Chapter order
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE studio.writing_categories (
    writing_id BIGINT NOT NULL REFERENCES studio.writings(id),
    category_id BIGINT NOT NULL REFERENCES studio.categories(id),
    PRIMARY KEY (writing_id, category_id)
);

CREATE TABLE studio.reviewer_invitations (
    id BIGSERIAL PRIMARY KEY,
    writing_id BIGINT NOT NULL REFERENCES studio.writings(id),
    invite_token VARCHAR(255) NOT NULL UNIQUE,
    reviewer_email VARCHAR(255) NOT NULL,
    is_redeemed BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE studio.reviewer_feedback (
    id BIGSERIAL PRIMARY KEY,
    writing_id BIGINT NOT NULL REFERENCES studio.writings(id),
    reviewer_id BIGINT NOT NULL, -- Points to identity.users(id)
    text_anchor_start INT NOT NULL, -- Selected text range coordinates
    text_anchor_end INT NOT NULL,
    selected_text TEXT NOT NULL,
    comment TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
```

### D. Schema: `catalog`
Tracks public directory indexing and followed channels.

```sql
-- Migration: V1__init_catalog.sql
CREATE SCHEMA IF NOT EXISTS catalog;

CREATE TABLE catalog.published_writings (
    id BIGINT PRIMARY KEY, -- Maps to studio.writings(id)
    tenant_id BIGINT NOT NULL,
    author_pen_name VARCHAR(100) NOT NULL,
    title VARCHAR(255),
    synopsis TEXT,
    cover_image_url VARCHAR(512),
    type VARCHAR(30) NOT NULL,
    price_type VARCHAR(20) NOT NULL,
    price_amount DECIMAL(10,2) NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE catalog.follows (
    id BIGSERIAL PRIMARY KEY,
    follower_id BIGINT NOT NULL, -- Reader User ID
    author_id BIGINT NOT NULL REFERENCES identity.author_profiles(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE(follower_id, author_id)
);
```

### E. Schema: `reading`
Manages reader shelves, purchase permissions, and progress.

```sql
-- Migration: V1__init_reading.sql
CREATE SCHEMA IF NOT EXISTS reading;

CREATE TABLE reading.library_entries (
    id BIGSERIAL PRIMARY KEY,
    reader_id BIGINT NOT NULL, -- identity.users(id)
    writing_id BIGINT NOT NULL, -- catalog.published_writings(id)
    last_read_chapter_id BIGINT,
    last_read_page_num INT DEFAULT 1,
    last_read_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE(reader_id, writing_id)
);

CREATE TABLE reading.unlocked_contents (
    id BIGSERIAL PRIMARY KEY,
    reader_id BIGINT NOT NULL,
    writing_id BIGINT NOT NULL, -- Chapter or Post ID
    unlocked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE(reader_id, writing_id)
);
```

### F. Schema: `billing`
Governs double-entry ledger bookkeeping, cash-outs, and promos.

```sql
-- Migration: V1__init_billing.sql
CREATE SCHEMA IF NOT EXISTS billing;

CREATE TABLE billing.wallets (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL UNIQUE, -- User ID or Platform System ID
    type VARCHAR(30) NOT NULL DEFAULT 'USER', -- 'USER', 'SYSTEM_COMMISSION'
    balance DECIMAL(15,2) DEFAULT 0.00 NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE billing.wallet_ledgers (
    id BIGSERIAL PRIMARY KEY,
    source_wallet_id BIGINT REFERENCES billing.wallets(id),
    destination_wallet_id BIGINT REFERENCES billing.wallets(id),
    amount DECIMAL(15,2) NOT NULL,
    type VARCHAR(30) NOT NULL, -- 'DEPOSIT', 'UNLOCK_PURCHASE', 'PAYOUT_WITHDRAWAL', 'COMMISSION_FEE'
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE billing.payout_requests (
    id BIGSERIAL PRIMARY KEY,
    author_id BIGINT NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'PROCESSED', 'REJECTED'
    payout_mfs_number VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
```

### G. Schema: `analytics`
Captures low-coupling async logs for views and scouting analytics.

```sql
-- Migration: V1__init_analytics.sql
CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE analytics.chapter_views (
    id BIGSERIAL PRIMARY KEY,
    writing_id BIGINT NOT NULL,
    reader_session_hash VARCHAR(255) NOT NULL,
    viewed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
```

---

## 3. Core Modulith Service Contracts (Interface Specifications)

Modules must only communicate with other modules using the interfaces declared inside their `api/contract/` packages.

### `identity::api-contract`
```java
package com.prishtha.mvp.identity.api.contract;

import com.prishtha.mvp.identity.api.dto.response.UserBasicInfoResponseDto;
import java.util.Set;

public interface UserService {
    boolean isUserActive(Long userId);
    boolean isAuthor(Long userId);
    UserBasicInfoResponseDto getUserBasicInfo(Long userId);
    Set<Long> filterAuthorIds(Set<Long> authorIds);
}
```

### `tenant::api-contract`
```java
package com.prishtha.mvp.tenant.api.contract;

import com.prishtha.mvp.tenant.api.dto.response.TenantThemeResponseDto;

public interface TenantService {
    boolean existsById(Long tenantId);
    TenantThemeResponseDto getThemeByDomain(String domain);
}
```

### `catalog::api-contract`
```java
package com.prishtha.mvp.catalog.api.contract;

import com.prishtha.mvp.catalog.api.dto.response.PublishedWritingDto;

public interface CatalogService {
    PublishedWritingDto getPublishedWriting(Long writingId);
    boolean isContentLocked(Long writingId);
}
```

### `billing::api-contract`
```java
package com.prishtha.mvp.billing.api.contract;

public interface WalletService {
    boolean hasSufficientBalance(Long userId, double amount);
    void debitForUnlock(Long userId, Long destinationWalletId, double amount, String idempotencyKey);
}
```

---

## 4. Scaling, Database, & Virtual Thread Configurations

### A. Spring Modulith Event Registry Setup
To prevent losing async events during server restarts, configure the database-backed publication registry in `build.gradle`:
```groovy
implementation 'org.springframework.modulith:spring-modulith-starter-jpa'
```
Modulith automatically creates a table `event_publication` to guarantee **at-least-once delivery** of `@ApplicationModuleListener` events.

### B. HikariCP Pool & Virtual Threads
Configure connection timeouts in `application.properties` to prevent carrier-thread pinning under heavy load:
```properties
# Enable Java Virtual Threads
spring.threads.virtual.enabled=true

# Database pool tuning
spring.datasource.hikari.maximum-pool-size=50
spring.datasource.hikari.minimum-idle=10
spring.datasource.hikari.idle-timeout=300000
spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.max-lifetime=1800000
```

### C. Flyway Scoped Partitioning
Configure Flyway to scan package migrations independently:
```properties
spring.flyway.locations=classpath:db/migration/identity,classpath:db/migration/tenant,classpath:db/migration/studio,classpath:db/migration/catalog,classpath:db/migration/reading,classpath:db/migration/billing,classpath:db/migration/analytics
```

---

## 5. Coding Phase 1: Step-by-Step Initialization Guide

Start by creating the database schemas and the initial signup endpoints. Follow this sequence exactly:

```
┌────────────────────────────────────────┐
│  1. Create DB Migrations               │ <-- Flyway V1 scripts for identity & tenant
└────────────────────────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────┐
│  2. Implement Tenant Context Filtering  │ <-- TenantContextHolder & domain MVC Interceptor
└────────────────────────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────┐
│  3. Code identity::internal Entities   │ <-- User, AuthorProfile entities & JPA repos
└────────────────────────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────┐
│  4. Write Identity OTP Signup Flow     │ <-- UserController registration + OTP verification
└────────────────────────────────────────┘
```
