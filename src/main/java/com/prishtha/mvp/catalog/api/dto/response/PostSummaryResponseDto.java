package com.prishtha.mvp.catalog.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;

@Builder
public record PostSummaryResponseDto(
        Long id,
        String slug,
        String title,
        String synopsis,
        String coverImageUrl,
        String priceType,
        BigDecimal priceAmount,
        Long authorId,
        String authorPenName,
        Instant publishedAt,
        long viewCount,
        long likeCount,
        long commentCount) {}
