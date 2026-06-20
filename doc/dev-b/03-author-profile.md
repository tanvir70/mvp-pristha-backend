# Dev B — Feature 03: Author Profile

**Branch:** `feature/b3-author-profile`  
**Module:** `identity`  
**Status:** implemented

---

## Goal

Provide author self-profile APIs and a public author profile endpoint.

---

## Implemented APIs

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/v1/authors/me` | Query param `requesterUserId` (temporary until JWT context) |
| `PATCH` | `/api/v1/authors/me` | Update pen name, bio, payout phone |
| `GET` | `/api/v1/authors/{authorProfileId}` | Public author profile |

---

## DTOs added

- Request:
  - `AuthorProfileUpdateRequestDto`
- Responses:
  - `AuthorProfileResponseDto`
  - `PublicAuthorProfileResponseDto`

---

## Business rules implemented

- `/me` endpoints require requester role `AUTHOR`
- Public endpoint returns only active author profiles (`is_active = true`)
- Public display name fallback:
  - `penName` if present
  - otherwise `user.fullName`
- Pen name uniqueness enforced on update

---

## Files added/updated

- Added:
  - `src/main/java/com/prishtha/mvp/identity/api/contract/AuthorProfileService.java`
  - `src/main/java/com/prishtha/mvp/identity/api/controller/AuthorProfileController.java`
  - `src/main/java/com/prishtha/mvp/identity/api/dto/request/AuthorProfileUpdateRequestDto.java`
  - `src/main/java/com/prishtha/mvp/identity/api/dto/response/AuthorProfileResponseDto.java`
  - `src/main/java/com/prishtha/mvp/identity/api/dto/response/PublicAuthorProfileResponseDto.java`
  - `src/main/java/com/prishtha/mvp/identity/internal/service/AuthorProfileServiceImpl.java`
- Updated:
  - `src/main/java/com/prishtha/mvp/identity/internal/repository/AuthorProfileRepository.java` (added `findByUser_Id`)

---

## Note for JWT integration

`requesterUserId` query params are temporary.  
Replace with authenticated principal user id once Dev A JWT context is merged.

