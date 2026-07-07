package com.prishtha.mvp.reading.api.dto.response;

import com.prishtha.mvp.reading.api.contract.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentAccessResponseDto {
    private String slug;
    private AccessLevel accessLevel;
    private String body;
}
