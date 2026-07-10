package com.prishtha.mvp.studio.api.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record WritingPublishedEvent(
        Long writingId,
        Long tenantId,
        Long authorId,
        Long parentId,
        String title,
        String slug,
        String synopsis,
        String coverImageUrl,
        String previewJson,
        String type,
        String status,
        String priceType,
        BigDecimal priceAmount,
        List<String> categoryNames,
        Instant publishedAt) {}
