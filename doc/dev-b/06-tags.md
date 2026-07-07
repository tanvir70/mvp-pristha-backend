# Dev B — Feature 06: Tags

**Branch:** `feature/b6-tags`  
**Module:** `catalog`  
**Status:** implemented

---

## Goal

Add platform tags, let authors assign tags to posts, and allow public feed filtering by tag.

---

## Implemented APIs

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/tags` | List all tags |
| `PUT` | `/api/v1/author/posts/{postId}/tags` | Assign tag slugs to an author's post |
| `GET` | `/api/v1/posts?tag={slug}` | Filter published feed by tag slug |

---

## Request body for assigning tags

```json
{
  "tagSlugs": ["fiction", "essay"]
}
```

Behavior:
- Replaces existing tags for the post
- Rejects unknown slug values

---

## Seed data

Added Flyway migration:
- `src/main/resources/db/migration/catalog/V2__seed_tags.sql`

Seeded tags:
- fiction
- poetry
- essay
- daily-thought

---

## Files added/updated

### Added
- `src/main/java/com/prishtha/mvp/catalog/internal/repository/TagRepository.java`
- `src/main/java/com/prishtha/mvp/catalog/internal/repository/PostTagRepository.java`
- `src/main/java/com/prishtha/mvp/catalog/api/dto/request/AssignPostTagsRequestDto.java`
- `src/main/java/com/prishtha/mvp/catalog/api/dto/response/TagResponseDto.java`
- `src/main/java/com/prishtha/mvp/catalog/api/contract/TagService.java`
- `src/main/java/com/prishtha/mvp/catalog/internal/service/TagServiceImpl.java`
- `src/main/java/com/prishtha/mvp/catalog/api/controller/TagController.java`
- `src/main/resources/db/migration/catalog/V2__seed_tags.sql`

### Updated
- `src/main/java/com/prishtha/mvp/catalog/internal/repository/PostRepository.java`
- `src/main/java/com/prishtha/mvp/catalog/api/contract/CatalogService.java`
- `src/main/java/com/prishtha/mvp/catalog/internal/service/CatalogServiceImpl.java`
- `src/main/java/com/prishtha/mvp/catalog/api/controller/CatalogController.java`
- `src/main/java/com/prishtha/mvp/catalog/api/contract/AuthorPostService.java`
- `src/main/java/com/prishtha/mvp/catalog/internal/service/AuthorPostServiceImpl.java`
- `src/main/java/com/prishtha/mvp/catalog/api/controller/AuthorPostController.java`

---

## Notes

- Current author ownership context still uses `authorProfileId` query param until JWT identity context is integrated.
- Feed still supports `q` search; `tag` filter is prioritized when both are sent.

