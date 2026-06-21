# Dev B — Feature 08: Admin Tools

**Branch:** `feature/b8-admin-tools`  
**Module:** `identity`, `catalog`  
**Status:** implemented

---

## Goal

Provide admin moderation tools to deactivate authors and flag posts for review.

---

## Implemented APIs

### Author moderation (`identity`)

| Method | Path | Description |
|---|---|---|
| `PATCH` | `/api/v1/admin/authors/{authorProfileId}/deactivate` | Deactivate author |
| `PATCH` | `/api/v1/admin/authors/{authorProfileId}/activate` | Reactivate author |

Query param: `adminUserId` (temporary until JWT admin context).

### Post moderation (`catalog`)

| Method | Path | Description |
|---|---|---|
| `PATCH` | `/api/v1/admin/posts/{postId}/review` | Set post to `UNDER_REVIEW` or restore to `PUBLISHED` |

Request body:

```json
{
  "status": "UNDER_REVIEW",
  "reviewNote": "Reported for plagiarism"
}
```

Supported statuses: `UNDER_REVIEW`, `PUBLISHED`.

---

## Business rules implemented

### Deactivate author
- Admin role required (`UserRole.ADMIN`)
- Sets `author_profiles.is_active = false`
- Deactivated authors cannot:
  - create/update/delete posts
  - publish/unpublish posts
  - assign tags
  - upload media
- Public author page already returns error for inactive authors

### Post under review
- Sets `posts.status = UNDER_REVIEW`
- Clears `publishedAt` when moved to review
- Public feed only shows `PUBLISHED` posts, so reviewed posts are hidden automatically
- Admin can restore to `PUBLISHED` via same endpoint

---

## Cross-module integration

- Added `AuthorProfileService.ensureAuthorIsActive(authorProfileId)` in identity
- Catalog module now depends on `identity::api-contract`
- `AuthorPostServiceImpl` and `MediaUploadServiceImpl` call active-author check before write actions

---

## Files added/updated

### Added
- `src/main/java/com/prishtha/mvp/identity/api/contract/AdminAuthorService.java`
- `src/main/java/com/prishtha/mvp/identity/internal/service/AdminAuthorServiceImpl.java`
- `src/main/java/com/prishtha/mvp/identity/api/controller/AdminAuthorController.java`
- `src/main/java/com/prishtha/mvp/catalog/api/contract/AdminPostService.java`
- `src/main/java/com/prishtha/mvp/catalog/internal/service/AdminPostServiceImpl.java`
- `src/main/java/com/prishtha/mvp/catalog/api/controller/AdminPostController.java`
- `src/main/java/com/prishtha/mvp/catalog/api/dto/request/AdminPostReviewRequestDto.java`

### Updated
- `src/main/java/com/prishtha/mvp/identity/api/contract/AuthorProfileService.java`
- `src/main/java/com/prishtha/mvp/identity/internal/service/AuthorProfileServiceImpl.java`
- `src/main/java/com/prishtha/mvp/catalog/package-info.java`
- `src/main/java/com/prishtha/mvp/catalog/internal/service/AuthorPostServiceImpl.java`
- `src/main/java/com/prishtha/mvp/catalog/internal/service/MediaUploadServiceImpl.java`

---

## Dev B backend complete

All 8 Dev B feature branches are now implemented:

1. `feature/b1-public-catalog-feed`
2. `feature/b2-author-request`
3. `feature/b3-author-profile`
4. `feature/b4-post-studio`
5. `feature/b5-publish-post`
6. `feature/b6-tags`
7. `feature/b7-media-upload`
8. `feature/b8-admin-tools`

Next step: merge PRs into `develop` in order, then integrate with Dev A (JWT, billing, access gate, social).
