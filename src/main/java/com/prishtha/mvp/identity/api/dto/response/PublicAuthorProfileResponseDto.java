package com.prishtha.mvp.identity.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicAuthorProfileResponseDto {
    private Long id;
    private Long userId;
    private String displayName;
    private String biography;
    private String avatarUrl;
}
