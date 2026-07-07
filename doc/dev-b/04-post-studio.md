# Dev B — Feature 04: Post Studio (Author CRUD)

**Branch:** `feature/b4-post-studio`  
**Module:** `catalog`  
**Status:** implemented

---

## Goal

Enable author-side draft post creation and management.

---

## Implemented APIs

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/author/posts` | Create draft post |
| `PUT` | `/api/v1/author/posts/{postId}` | Update draft post |
| `GET` | `/api/v1/author/posts` | List author's non-deleted posts |
| `GET` | `/api/v1/author/posts/{postId}` | Get author's post by id |
| `DELETE` | `/api/v1/author/posts/{postId}` | Soft delete post |

Current ownership key is `authorProfileId` query param (temporary until JWT context is wired).

---

## Added DTOs

- Request:
  - `AuthorPostUpsertRequestDto`
- Response:
  - `AuthorPostResponseDto`

---

## Service & repository

- Added `AuthorPostService` + `AuthorPostServiceImpl`
- Updated `PostRepository` with author-scoped methods:
  - `findByIdAndAuthorIdAndDeletedAtIsNull`
  - `findByAuthorIdAndDeletedAtIsNullOrderByUpdatedAtDesc`
  - `existsBySlug`

---

## Business rules implemented

- Post body required (non-empty)
- Draft-only editing (`status = DRAFT`)
- Pricing validation:
  - `FREE` => `priceAmount` must be null
  - `LOCKED` => `priceAmount >= 1`
- Slug auto-generation with collision handling (`-2`, `-3`, ...)
- `bodyPlainText` sync from body for search support
- Soft delete via `deletedAt`

---

## Files added/updated

- Added:
  - `src/main/java/com/prishtha/mvp/catalog/api/contract/AuthorPostService.java`
  - `src/main/java/com/prishtha/mvp/catalog/api/controller/AuthorPostController.java`
  - `src/main/java/com/prishtha/mvp/catalog/api/dto/request/AuthorPostUpsertRequestDto.java`
  - `src/main/java/com/prishtha/mvp/catalog/api/dto/response/AuthorPostResponseDto.java`
  - `src/main/java/com/prishtha/mvp/catalog/internal/service/AuthorPostServiceImpl.java`
- Updated:
  - `src/main/java/com/prishtha/mvp/catalog/internal/repository/PostRepository.java`

---

## Note for next features

Publishing (DRAFT -> PUBLISHED) is intentionally not included here.  
Implement that in Feature 05.

