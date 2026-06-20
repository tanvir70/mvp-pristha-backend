# Dev B — Feature 02: Author Request & Admin Approval

**Branch:** `feature/b2-author-request`  
**Module:** `identity`  
**Status:** implemented

---

## Goal

Allow a reader to request author access, and allow admin to approve/reject that request.

---

## Implemented APIs

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/author-requests` | Submit request (`requesterUserId` query param for now) |
| `GET` | `/api/v1/author-requests/me` | Get latest request by user |
| `GET` | `/api/v1/admin/author-requests` | List requests by status |
| `PATCH` | `/api/v1/admin/author-requests/{requestId}/approve` | Approve with `adminUserId` |
| `PATCH` | `/api/v1/admin/author-requests/{requestId}/reject` | Reject with optional `reviewNote` |

---

## Request DTOs

- `AuthorRequestSubmitRequestDto`
  - `requestedPenName` (optional)
  - `motivation` (optional)
  - `sampleWritingUrl` (optional)
- `AuthorRequestRejectRequestDto`
  - `reviewNote` (optional)

Response DTO:
- `AuthorRequestResponseDto`

---

## Business rules implemented

- Only `VERIFIED` users can submit author request
- `AUTHOR` users cannot submit new request
- Only one `PENDING` request per user
- Approve path:
  - checks admin role (`UserRole.ADMIN`)
  - creates `AuthorProfile`
  - sets user role to `AUTHOR`
  - marks request `APPROVED` with reviewer metadata
- Reject path:
  - checks admin role
  - marks request `REJECTED` with reviewer metadata

---

## Files added/updated

- Added:
  - `src/main/java/com/prishtha/mvp/identity/api/contract/AuthorRequestService.java`
  - `src/main/java/com/prishtha/mvp/identity/api/controller/AuthorRequestController.java`
  - `src/main/java/com/prishtha/mvp/identity/api/controller/AdminAuthorRequestController.java`
  - `src/main/java/com/prishtha/mvp/identity/api/dto/request/AuthorRequestSubmitRequestDto.java`
  - `src/main/java/com/prishtha/mvp/identity/api/dto/request/AuthorRequestRejectRequestDto.java`
  - `src/main/java/com/prishtha/mvp/identity/api/dto/response/AuthorRequestResponseDto.java`
  - `src/main/java/com/prishtha/mvp/identity/internal/repository/AuthorRequestRepository.java`
  - `src/main/java/com/prishtha/mvp/identity/internal/service/AuthorRequestServiceImpl.java`
- Updated:
  - `src/main/java/com/prishtha/mvp/identity/internal/repository/AuthorProfileRepository.java`

---

## Note for upcoming JWT integration

For now, endpoints take `requesterUserId` / `adminUserId` query params because JWT current-user extraction is not merged yet.  
After Dev A completes JWT context, replace those query params with authenticated principal.

