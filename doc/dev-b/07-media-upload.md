# Dev B — Feature 07: Media Upload

**Branch:** `feature/b7-media-upload`  
**Module:** `catalog`  
**Status:** implemented

---

## Goal

Allow authors to upload images from the post editor and receive a URL to embed in post content.

---

## Implemented API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/author/media` | Upload image (`multipart/form-data`) |

### Form fields

| Field | Type | Required |
|---|---|---|
| `authorProfileId` | query param | yes |
| `file` | file | yes |

### Example response

```json
{
  "id": 10,
  "url": "/uploads/authors/3/a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg",
  "storageKey": "authors/3/a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg",
  "mimeType": "image/jpeg",
  "fileSizeBytes": 245000
}
```

---

## Rules implemented

- Max file size: **5 MB**
- Allowed types: `image/jpeg`, `image/png`, `image/webp`
- Stores file locally under `uploads/authors/{authorProfileId}/`
- Persists metadata in `catalog.post_media`
- Serves files at `/uploads/**` (public read)

---

## Configuration

Added to `application.properties`:

```properties
catalog.media.upload-dir=uploads
catalog.media.max-file-size-bytes=5242880
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=5MB
```

---

## Files added/updated

### Added
- `src/main/java/com/prishtha/mvp/catalog/api/contract/MediaUploadService.java`
- `src/main/java/com/prishtha/mvp/catalog/api/controller/MediaUploadController.java`
- `src/main/java/com/prishtha/mvp/catalog/api/dto/response/MediaUploadResponseDto.java`
- `src/main/java/com/prishtha/mvp/catalog/internal/service/MediaUploadServiceImpl.java`
- `src/main/java/com/prishtha/mvp/catalog/internal/repository/PostMediaRepository.java`
- `src/main/java/com/prishtha/mvp/catalog/internal/config/MediaStorageProperties.java`
- `src/main/java/com/prishtha/mvp/catalog/internal/config/CatalogConfig.java`
- `src/main/java/com/prishtha/mvp/catalog/internal/config/CatalogWebConfig.java`

### Updated
- `src/main/java/com/prishtha/mvp/identity/internal/config/SecurityConfig.java`
  - permit `/uploads/**` and `/api/v1/tags/**`
- `src/main/resources/application.properties`

---

## Notes

- `authorProfileId` query param is temporary until JWT current-user context is integrated.
- `post_id` on `post_media` remains null until the post is saved and media is linked (future enhancement).
- MinIO can replace local storage later without changing the API contract.

---

## Next feature

Feature 08: Admin tools (`feature/b8-admin-tools`)
