# Dev B — Feature 01: Public Catalog & Feed

**Branch:** `feature/b1-public-catalog-feed`  
**Module:** `catalog`  
**Estimated time:** 2-3 days  
**Depends on:** none (public endpoints)

---

## Goal

Implement public APIs so guests and logged-in users can browse published posts without authentication.

---

## APIs implemented

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/posts` | Public | Paginated feed of published posts |
| GET | `/api/v1/posts/{slug}` | Public | Published post metadata by slug |

---

## Response scope

Public APIs intentionally return metadata only:

- `id`, `slug`, `title`, `excerpt`
- `coverImageUrl`, `pricingType`, `priceAmount`
- `authorId`, `publishedAt`
- `viewCount`, `likeCount`, `commentCount`

They do **not** return `body` or `previewBody`. Content gating is owned by Dev A.

---

## Implementation notes

- Query only `status = PUBLISHED` and `deleted_at IS NULL`
- Ordered by `published_at DESC`
- Supports optional `q` search on `title`, `excerpt`, `bodyPlainText`
- Security updated to allow `/api/v1/posts/**` without JWT

---

## Added files

- `src/main/java/com/prishtha/mvp/catalog/api/contract/CatalogService.java`
- `src/main/java/com/prishtha/mvp/catalog/api/controller/CatalogController.java`
- `src/main/java/com/prishtha/mvp/catalog/api/dto/response/PostSummaryResponseDto.java`
- `src/main/java/com/prishtha/mvp/catalog/api/dto/response/PostDetailResponseDto.java`
- `src/main/java/com/prishtha/mvp/catalog/internal/repository/PostRepository.java`
- `src/main/java/com/prishtha/mvp/catalog/internal/service/CatalogServiceImpl.java`

---

## Next step

Create PR for this branch, then continue with Feature 02 (Author request) after Dev A's JWT work is merged.
