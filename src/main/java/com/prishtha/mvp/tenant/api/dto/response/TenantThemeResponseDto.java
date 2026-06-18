package com.prishtha.mvp.tenant.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantThemeResponseDto {
    private String brandLogoUrl;
    private String primaryColor;
    private String secondaryColor;
    private String customStylesheetUrl;
}
