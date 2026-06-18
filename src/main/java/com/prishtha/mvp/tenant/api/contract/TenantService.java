package com.prishtha.mvp.tenant.api.contract;

import com.prishtha.mvp.tenant.api.dto.response.TenantThemeResponseDto;
import java.util.Optional;

public interface TenantService {
    boolean existsById(Long tenantId);
    TenantThemeResponseDto getThemeByDomain(String domain);
    Optional<Long> resolveTenantIdByDomain(String domain);
}
