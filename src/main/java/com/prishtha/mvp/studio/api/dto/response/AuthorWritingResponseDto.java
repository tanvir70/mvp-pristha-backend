package com.prishtha.mvp.studio.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;

@Builder
public record AuthorWritingResponseDto(
        Long id,
        Long authorId,
        Long parentId,
        String slug,
        String title,
        String synopsis,
        String bodyJson,
        String previewJson,
        String coverImageUrl,
        String type,
        String priceType,
        BigDecimal priceAmount,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {}
