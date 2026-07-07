package com.prishtha.mvp.identity.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBasicInfoResponseDto {
    private Long id;
    private String phone;
    private String fullName;
    private String status;
}
