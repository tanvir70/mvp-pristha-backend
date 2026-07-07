package com.prishtha.mvp.catalog.api.dto.response;

import lombok.Builder;

@Builder
public record MediaUploadResponseDto(
        Long id, String url, String storageKey, String mimeType, int fileSizeBytes) {}
