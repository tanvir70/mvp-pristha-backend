package com.prishtha.mvp.catalog.api.dto.response;

import com.prishtha.mvp.catalog.internal.entity.PricingType;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;

@Builder
public record PostDetailResponseDto(
        Long id,
        String slug,
        String title,
        String excerpt,
        String coverImageUrl,
        PricingType pricingType,
        BigDecimal priceAmount,
        Long authorId,
        Instant publishedAt,
        long viewCount,
        int likeCount,
        int commentCount) {}
