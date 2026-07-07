package com.prishtha.mvp.studio.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WritingContentResponseDto {
    private Long writingId;
    private String slug;
    private String priceType;
    private String bodyJson;
    private String previewJson;
}
