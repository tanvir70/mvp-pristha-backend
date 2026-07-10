package com.prishtha.mvp.catalog.internal.service;

import com.prishtha.mvp.catalog.api.contract.MediaUploadService;
import com.prishtha.mvp.catalog.api.dto.response.MediaUploadResponseDto;
import com.prishtha.mvp.catalog.internal.config.MediaStorageProperties;
import com.prishtha.mvp.catalog.internal.entity.WritingMedia;
import com.prishtha.mvp.catalog.internal.repository.WritingMediaRepository;
import com.prishtha.mvp.identity.api.contract.AuthorProfileService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
class MediaUploadServiceImpl implements MediaUploadService {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    private final WritingMediaRepository writingMediaRepository;
    private final MediaStorageProperties mediaStorageProperties;
    private final AuthorProfileService authorProfileService;

    @Override
    public MediaUploadResponseDto uploadImage(Long authorProfileId, MultipartFile file) {
        authorProfileService.ensureAuthorIsActive(authorProfileId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new IllegalArgumentException("Only JPEG, PNG, and WebP images are allowed");
        }

        if (file.getSize() > mediaStorageProperties.getMaxFileSizeBytes()) {
            throw new IllegalArgumentException("File size exceeds 5MB limit");
        }

        String extension = resolveExtension(mimeType);
        String storageKey = "authors/" + authorProfileId + "/" + UUID.randomUUID() + extension;
        Path targetPath = Paths.get(mediaStorageProperties.getUploadDir(), storageKey);

        try {
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, file.getBytes());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store uploaded file", ex);
        }

        WritingMedia writingMedia = new WritingMedia();
        writingMedia.setAuthorId(authorProfileId);
        writingMedia.setStorageKey(storageKey);
        writingMedia.setMimeType(mimeType);
        writingMedia.setFileSizeBytes((int) file.getSize());

        WritingMedia saved = writingMediaRepository.save(writingMedia);
        String url = "/uploads/" + storageKey;

        return MediaUploadResponseDto.builder()
                .id(saved.getId())
                .url(url)
                .storageKey(storageKey)
                .mimeType(mimeType)
                .fileSizeBytes(saved.getFileSizeBytes())
                .build();
    }

    private String resolveExtension(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }
}
