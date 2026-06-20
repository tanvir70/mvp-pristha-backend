# Pristha Digital: Coding Guidelines & Architectural Standards

This document defines the coding standards, patterns, and architectural rules for the **Pristha Digital (Prishtha)** backend. All developers must follow these guidelines to maintain package modularity, security, and scalability.

---

## 1. Architectural Design (Spring Modulith)

We use **Spring Modulith** to enforce clean boundaries within a single monolithic codebase. This ensures the system can be easily split into microservices in the future.

### A. Directory & Package Structure
Every domain module (e.g., `identity`, `tenant`, `catalog`) must follow this exact structure:

```
com.prishtha.mvp.[module]/
  ├── package-info.java           <-- Configures allowed imports for this module
  │
  ├── api/                       <-- Public API layer (Visible to other modules)
  │    ├── contract/
  │    │    ├── UserService.java   <-- Public interface definition
  │    │    └── package-info.java  <-- Exposes [module]::api-contract
  │    │
  │    ├── dto/
  │    │    ├── request/
  │    │    │    └── package-info.java  <-- Exposes [module]::api-request-dto
  │    │    └── response/
  │    │         └── package-info.java  <-- Exposes [module]::api-response-dto
  │    │
  │    └── event/
  │         └── package-info.java  <-- Exposes [module]::api-event
  │
  └── internal/                  <-- Private Implementation (Hidden from other modules)
       ├── config/               <-- Configurations specific to this module
       ├── entity/               <-- JPA Database Entities (Never expose to other modules)
       ├── repository/           <-- Spring Data Repositories
       ├── service/              <-- Implementations (Must be package-private)
       ├── mapper/               <-- MapStruct mapping interfaces
       └── exception/            <-- Exceptions specific to this module
```

---

### B. Encapsulation Rules
1. **Never Expose Internal Packages**: Classes inside `internal/` must be package-private (omit the `public` access modifier). Other modules must **never** import them.
2. **Interface-Based Calls**: Modules must only communicate with other modules via the interfaces exposed in `api/contract/`.
3. **DTOs for Data Exchange**: Never expose raw JPA `@Entity` classes through public APIs or REST controllers. Translate them to DTOs in `api/dto/` before sharing.
4. **Restricting Dependencies**: Declare which modules your module is allowed to depend on in the root package's `package-info.java`:
   ```java
   @org.springframework.modulith.ApplicationModule(
       displayName = "Billing Module",
       allowedDependencies = {
           "shared",
           "identity::api-contract",
           "identity::api-response-dto"
       })
   package com.prishtha.mvp.billing;
   ```

---

### C. Shared Code (`shared`)
For utility classes, global exceptions, and common base classes, use the `shared` package. It is configured as an **Open Module**:
```java
@org.springframework.modulith.ApplicationModule(
    displayName = "Shared Module",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.prishtha.mvp.shared;
```

---

## 2. Coding Standards & Implementation Patterns

### A. Java & Spring Boot Conventions
* **Language Level**: Java 25.
* **Framework**: Spring Boot 4.x.
* **Virtual Threads (Loom)**: We write simple, blocking/imperative code instead of complex reactive WebFlux code. 
  - *Rule*: Avoid long `synchronized` blocks that can pin virtual threads. Use `ReentrantLock` if locking is necessary.
* **Code Style**: Follow the Google Java Style guide. Format code using the IDE shortcut (`Ctrl + Alt + L`).

### B. JPA & Database Design
* **Database Isolation**: Keep tables separated by database schemas matching the module name.
  ```java
  @Entity
  @Table(name = "books", schema = "catalog")
  public class Book extends BaseEntity { ... }
  ```
* **Primary Keys**: Extend `BaseEntity` from the `shared` module, which should define standard columns (`id` as `Long`, `createdAt`, `updatedAt`).
* **Migrations**: Use **Flyway** for all database schema migrations. Migration scripts reside under `src/main/resources/db/migration` and must be named chronologically.
* **Tenant Isolation**: Every database table representing tenant data must include a `tenant_id` column to enforce strict isolation.

### C. Cross-Schema Boundaries & Soft References
To maintain Spring Modulith's module isolation and support future database separation, cross-schema relationships must be handled carefully.

1. **Physical FK Restrictions**: 
   * **Within a single schema**: Use standard foreign keys (`REFERENCES` in SQL, `@ManyToOne` / `@JoinColumn` in JPA) to link entities.
   * **Across different schemas**: Use **Soft References** (a plain `BIGINT` in SQL, a plain `Long` in Java) without database constraints.
2. **JPA Joins**: Never write `@Query` joins or use entity relationships that span across different schemas. Modulith verification tests will fail if Java entities import internal types of other modules.
3. **Application Validation**: Before creating a record with a soft reference, the service layer must validate the target ID using the public contract of the owning module:
   ```java
   if (!identityService.existsById(dto.getAuthorId())) {
       throw new EntityNotFoundException("Author not found");
   }
   ```
4. **Data Integrity & Deletion**:
   * Core entities should use **Soft Deletion** (flagging `deletedAt`) rather than hard SQL deletions, ensuring other schemas' soft references are not orphaned.
   * If a hard deletion is executed, publish a transactional Modulith event (e.g., `UserDeletedEvent`) to clean up or nullify dependent soft references asynchronously in other modules.
5. **Denormalization for Reads**: If a cross-schema read is slow or required for sorting/searching (e.g., sorting catalog listings by author pen name), denormalize the field directly in the reading schema. Update it asynchronously via event listeners.

### D. Mapping Layer (MapStruct)
Use **MapStruct** for translating between JPA entities and public DTOs.
* Place mapping interfaces under `internal/mapper/`.
* Configure them as Spring beans:
  ```java
  @Mapper(componentModel = "spring")
  public interface BookMapper {
      BookDto toDto(Book book);
      Book toEntity(BookDto dto);
  }
  ```

### E. Decoupled Event-Driven Communication
To avoid tight coupling during side-effects (e.g. updating analytics when a user unlocks a book):
1. Publish a Spring Application Event in the service class:
   ```java
   applicationEventPublisher.publishEvent(new BookUnlockedEvent(bookId, readerId));
   ```
2. Listen to the event in the receiving module:
   ```java
   @ApplicationModuleListener
   public void handleBookUnlocked(BookUnlockedEvent event) {
       // update reader metrics...
   }
   ```
   *Note: Use `@ApplicationModuleListener` instead of standard `@EventListener` because it auto-handles transaction boundaries and guarantees asynchronous event processing safety in Spring Modulith.*

---

## 3. Architecture Verification & Tests

To ensure code boundaries do not deteriorate over time, we enforce verification via the `ModulithVerificationTests` class.
* Run `./gradlew test` regularly.
* The test will **fail** if any developer breaks encapsulation (e.g. importing an internal class of another module).
* The tests automatically write up-to-date PlantUML diagrams of the package architecture under `build/spring-modulith-docs/`. Refer to these diagrams to visualize dependencies.
