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
* **Associations**: Always declare `@ManyToOne`/`@OneToOne` as `FetchType.LAZY` explicitly — the JPA default for `@ManyToOne` is `EAGER`, which causes unbounded N+1 loading as the graph grows.
  ```java
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "author_profile_id")
  private AuthorProfile authorProfile;
  ```
* **Enums**: Always map enum columns with `@Enumerated(EnumType.STRING)`. The default `ORDINAL` strategy silently corrupts existing rows if a constant is ever inserted or reordered.
  ```java
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private PostStatus status;
  ```

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
* **Partial updates**: For PATCH-style updates, add an `update*(@MappingTarget Entity target, RequestDto dto)` method with `@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)`, so fields the client omitted (null in the DTO) are left untouched on the entity instead of being wiped out.
  ```java
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void updateFromDto(@MappingTarget Book book, BookUpdateRequestDto dto);
  ```
* **Lightweight projections**: For dropdown/list views that only need a few fields (id, name, etc.), add a `toBasicInfoDto(Entity entity)` method returning a `*BasicInfoResponseDto` rather than reusing the full response DTO — keeps payloads small and avoids mapping unused associations.

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

### F. REST Controllers
* **Location**: `api/controller/`. One controller per resource/aggregate, injected with the module's `api/contract` interface only (never an `internal/service` class directly).
* **Routing**: Class-level `@RequestMapping` reads its path from a per-module `RouteConstant` class instead of an inline literal — keeps every sub-path for a resource in one place and avoids typos drifting across controllers. Place it at `internal/util/constant/<Module>RouteConstant.java`:
  ```java
  public final class CatalogRouteConstant {
      private CatalogRouteConstant() {}

      public static final String POSTS_BASE_PATH = "/api/v1/posts";
      public static final String AUTHOR_POSTS_BASE_PATH = "/api/v1/author/posts";
      public static final String BY_ID = "/{postId}";
      public static final String PUBLISH = "/{postId}/publish";
  }
  ```
  ```java
  @RestController
  @RequestMapping(AUTHOR_POSTS_BASE_PATH)
  @RequiredArgsConstructor
  public class AuthorPostController {

      @PatchMapping(PUBLISH)
      public ResponseEntity<AuthorPostResponseDto> publishPost(@PathVariable Long postId) { /* ... */ }
  }
  ```
* **Return types**: No generic response envelope. Return the DTO (or `Page<Dto>`) directly for simple reads; wrap in `ResponseEntity<Dto>` when the HTTP status carries meaning (`201 Created` on create, `204 No Content` on delete, `200 OK` via `ResponseEntity.ok(...)` otherwise). HTTP status is the success/failure signal — errors are handled separately via `ProblemDetail` (see §J), not by wrapping the payload.
  ```java
  @PostMapping
  public ResponseEntity<AuthorPostResponseDto> createDraftPost(@RequestBody AuthorPostUpsertRequestDto requestDto) {
      AuthorPostResponseDto created = authorPostService.createDraftPost(requestDto);
      return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }
  ```
* **Pagination**: Accept `page`/`size` `@RequestParam`s with defaults, build a `Pageable` via `PageRequest.of(page, size)`, return `Page<Dto>` directly.
* Use `@PathVariable` for the resource's own id, `@RequestParam` for filters, and `@RequestBody` for write payloads.

### G. DTO Conventions
* **Type**: Plain classes (not records), annotated `@Data @Builder @NoArgsConstructor @AllArgsConstructor`. Records are avoided for DTOs in this codebase so partial construction via the builder stays available everywhere.
* **Location & naming**: `api/dto/request/*RequestDto.java` and `api/dto/response/*ResponseDto.java`. Never reuse a JPA `@Entity` as a DTO.
* **Validation**: Apply Jakarta validation annotations (`@NotBlank`, `@NotNull`, `@Size`, etc.) directly on request DTO fields; controllers validate with `@Valid` on the `@RequestBody` parameter.
* **Mapping**: Entity ↔ DTO conversion goes through a MapStruct mapper in `internal/mapper/` (see §D) — not hand-written `toDto()` helpers on the service. Existing modules with manual builder-based mapping predate this rule and should be migrated to a `@Mapper(componentModel = "spring")` interface when touched.

### H. Service Layer
* **Split**: Public interface in `api/contract/`, implementation in `internal/service/`. The implementation class has **no `public` modifier** (package-private), per the encapsulation rule in §B.
* **Annotations**: `@Service @RequiredArgsConstructor` plus a class-level `@Transactional`; override individual read methods with `@Transactional(readOnly = true)`.
  ```java
  @Service
  @RequiredArgsConstructor
  @Transactional
  class AuthorProfileServiceImpl implements AuthorProfileService {

      private final AuthorProfileRepository authorProfileRepository;

      @Override
      @Transactional(readOnly = true)
      public AuthorProfileResponseDto getMyAuthorProfile(Long requesterUserId) { /* ... */ }
  }
  ```
* **Cross-module calls**: Inject the other module's `api/contract` interface as a constructor dependency, exactly like a repository — never reach into another module's `internal` package.
* **Not-found / validation errors**: Look up via `repository.findById(...).orElseThrow(() -> new IllegalArgumentException("<message>"))`. See §J for how these surface as HTTP responses.

### I. Repository & Dynamic Queries
* Repositories are plain `JpaRepository<Entity, Long>` interfaces in `internal/repository/`, no implementation class. Prefer Spring Data derived query methods (`findByStatusAndDeletedAtIsNullOrderByPublishedAtDesc`) for simple lookups; drop to `@Query` (JPQL) for joins/projections that derived names can't express.
* **Specification pattern for combinatorial filters**: once a query needs more than two independent optional filters (e.g. search text + tag + status + author), stop branching with `if/else` across multiple repository methods. Instead extend `JpaSpecificationExecutor<Entity>` and build composable, null-safe `Specification<Entity>` factory methods in `internal/specification/`:
  ```java
  public class PostSpecification {
      public static Specification<Post> hasTagSlug(String tagSlug) {
          return (root, query, cb) ->
              tagSlug == null ? null : cb.equal(root.join("tags").get("slug"), tagSlug);
      }
  }
  ```
  Combine specifications with `Specification.where(...).and(...)` in the service; each method returns `null` when its criterion doesn't apply so Spring Data ignores it.

### J. Exception Handling
Errors use Spring's `ProblemDetail` (RFC 7807, `application/problem+json`) — no custom error envelope. `spring.mvc.problemdetails.enabled=true` is set so framework-level exceptions (validation failures, 404s on unknown routes, etc.) already render as `ProblemDetail` automatically.
* Throw `com.prishtha.mvp.shared.exception.EntityNotFoundException` / `BusinessRuleViolationException` from services instead of bare `IllegalArgumentException`, so the HTTP status is derived from the exception type rather than its message. `IllegalArgumentException` is still mapped (→ 400) as a fallback for code that hasn't migrated yet.
* `com.prishtha.mvp.shared.exception.GlobalExceptionHandler` (`@RestControllerAdvice`) is the single place these are mapped: `EntityNotFoundException` → 404, `BusinessRuleViolationException` → 400, each via `ProblemDetail.forStatusAndDetail(status, ex.getMessage())`. Modules should not define their own advice unless they need a module-specific error shape.

### K. Naming, Lombok & Logging
* **Entities**: `@Getter @Setter` only — never `@Data` (avoids generated `equals`/`hashCode`/`toString` pulling in lazy associations).
* **DTOs**: `@Data @Builder @NoArgsConstructor @AllArgsConstructor` (see §G) — fine here since DTOs hold no lazy relations.
* **Service/Controller wiring**: always constructor injection via `@RequiredArgsConstructor`; never field `@Autowired`.
* **Logging**: `@Slf4j` on the class, `log.info/warn/error(...)` with `{}` placeholders — no string concatenation in log calls.
* **Naming**: PascalCase classes, camelCase methods/fields, `UPPER_SNAKE_CASE` constants. Suffixes: `*RequestDto`, `*ResponseDto`, `*Event`, `*Specification`, `*ServiceImpl`.
* **Null handling**: prefer `Optional<T>` returns from repositories and `.orElseThrow(...)` at the call site over manual null checks.

### L. Async, Caching & Messaging (adopt when the need arrives)
None of this is wired up yet — no Redis or message broker dependency exists in `build.gradle` today. These conventions are recorded now so that whichever module first needs them adopts a consistent shape instead of improvising one. Don't add the dependency or the code until a real use case lands.
* **`@Async`**: Declare the method on the `api/contract`/`internal/service` interface as `@Async`, implement it returning `void` or `CompletableFuture<T>`. Requires a single `@EnableAsync` on the main application class (or a shared config) — don't configure an executor per call site.
* **Caching**: Use `@Cacheable`/`@CacheEvict` on service methods, with cache names and keys pulled from constants (e.g. `internal/util/constant/<Module>CacheConstant.java`) rather than inline string literals, so a rename doesn't require grepping every annotation.
* **Redis**: Never call `RedisTemplate`/`StringRedisTemplate` directly from a service. Define a `CacheService` contract (in `api/contract/` if other modules need it, otherwise `internal/`) and put the actual `StringRedisTemplate` calls behind a single `RedisService` implementation — keeps Redis swappable and testable.
* **RabbitMQ**: For cross-module work that should run out-of-band (not the immediate-consistency case `@ApplicationModuleListener` already covers in §E — use that first), declare queues/exchanges in `internal/config/<Module>RabbitMQConfig.java` and consume with `@RabbitListener(queues = ...)` methods in `internal/async/consumer/`. Keep the listener method thin — delegate to a service method immediately so retry/error semantics aren't entangled with business logic.

### M. Factory Pattern (adopt when the need arrives)
When a service needs to pick between several interchangeable implementations based on a runtime type/enum discriminator (e.g. different handling per `ChangeType` or per payment provider), don't grow an `if/else`-per-call-site chain. Add a `@Component` factory in `internal/factory/` that resolves the right implementation via a `switch` expression:
```java
@Component
@RequiredArgsConstructor
class PayoutStrategyFactory {
    private final BankPayoutStrategy bankPayoutStrategy;
    private final MobileWalletPayoutStrategy mobileWalletPayoutStrategy;

    PayoutStrategy resolve(PayoutMethod method) {
        return switch (method) {
            case BANK_TRANSFER -> bankPayoutStrategy;
            case MOBILE_WALLET -> mobileWalletPayoutStrategy;
        };
    }
}
```
Each strategy implementation stays a normal `@Component`/`@Service` implementing a shared interface — the factory's only job is picking one, not containing branch logic itself.

---

## 3. Architecture Verification & Tests

To ensure code boundaries do not deteriorate over time, we enforce verification via the `ModulithVerificationTests` class.
* Run `./gradlew test` regularly.
* The test will **fail** if any developer breaks encapsulation (e.g. importing an internal class of another module).
* The tests automatically write up-to-date PlantUML diagrams of the package architecture under `build/spring-modulith-docs/`. Refer to these diagrams to visualize dependencies.
