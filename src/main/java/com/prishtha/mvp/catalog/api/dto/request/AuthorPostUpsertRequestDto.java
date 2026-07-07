package com.prishtha.mvp.catalog.api.dto.request;

import com.prishtha.mvp.catalog.internal.entity.PricingType;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorPostUpsertRequestDto {
    private String title;
    private String excerpt;
    private String body;
    private String previewBody;
    private String coverImageUrl;
    private PricingType pricingType;
    private BigDecimal priceAmount;
}
