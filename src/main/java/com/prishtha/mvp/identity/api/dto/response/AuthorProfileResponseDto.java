package com.prishtha.mvp.identity.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorProfileResponseDto {
    private Long id;
    private Long userId;
    private String penName;
    private String biography;
    private String payoutPhone;
    private boolean active;
}
