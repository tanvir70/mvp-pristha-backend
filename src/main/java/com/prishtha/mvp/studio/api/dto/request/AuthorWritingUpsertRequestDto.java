package com.prishtha.mvp.studio.api.dto.request;

import com.prishtha.mvp.studio.internal.enums.PriceType;
import com.prishtha.mvp.studio.internal.enums.WritingType;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorWritingUpsertRequestDto {
    private String title;
    private String synopsis;
    private String bodyJson;
    private String previewJson;
    private String coverImageUrl;
    private WritingType type;
    private Long parentId;
    private PriceType priceType;
    private BigDecimal priceAmount;
}
