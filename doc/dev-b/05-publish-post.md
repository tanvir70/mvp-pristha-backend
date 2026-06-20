# Dev B — Feature 05: Publish / Unpublish Post

**Branch:** `feature/b5-publish-post`  
**Module:** `catalog`  
**Status:** implemented

---

## Goal

Allow authors to move posts between `DRAFT` and `PUBLISHED` with validation.

---

## Implemented APIs

| Method | Path | Notes |
|---|---|---|
| `PATCH` | `/api/v1/author/posts/{postId}/publish` | Publish a draft post |
| `PATCH` | `/api/v1/author/posts/{postId}/unpublish` | Move published post back to draft |

Both endpoints use `authorProfileId` query param for ownership check (temporary until JWT current-user is available).

---

## Rules implemented

### Publish
- Post must belong to current author
- Post must not be soft-deleted
- Post must not already be `PUBLISHED`
- Body must be non-empty
- Pricing must be valid:
  - `FREE` => no `priceAmount`
  - `LOCKED` => `priceAmount >= 1`
- On success:
  - `status = PUBLISHED`
  - `publishedAt = now()`

### Unpublish
- Post must belong to current author
- Post must currently be `PUBLISHED`
- On success:
  - `status = DRAFT`
  - `publishedAt = null`

---

## Files updated

- `src/main/java/com/prishtha/mvp/catalog/api/contract/AuthorPostService.java`
  - added publish/unpublish methods
- `src/main/java/com/prishtha/mvp/catalog/internal/service/AuthorPostServiceImpl.java`
  - implemented publish/unpublish logic
- `src/main/java/com/prishtha/mvp/catalog/api/controller/AuthorPostController.java`
  - added publish/unpublish endpoints

---

## Next feature

Feature 06: Tags (`feature/b6-tags`)

