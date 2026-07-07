package com.prishtha.mvp.catalog.api.dto.response;

import com.prishtha.mvp.catalog.internal.entity.PricingType;
import com.prishtha.mvp.catalog.internal.entity.PostStatus;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;

@Builder
public record AuthorPostResponseDto(
        Long id,
        Long authorId,
        String slug,
        String title,
        String excerpt,
        String body,
        String previewBody,
        String coverImageUrl,
        PricingType pricingType,
        BigDecimal priceAmount,
        PostStatus status,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {}
