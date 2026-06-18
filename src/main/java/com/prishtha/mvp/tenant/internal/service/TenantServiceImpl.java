package com.prishtha.mvp.tenant.internal.service;

import com.prishtha.mvp.tenant.api.contract.TenantService;
import com.prishtha.mvp.tenant.api.dto.response.TenantThemeResponseDto;
import com.prishtha.mvp.tenant.internal.entity.TenantDomain;
import com.prishtha.mvp.tenant.internal.entity.TenantTheme;
import com.prishtha.mvp.tenant.internal.repository.TenantDomainRepository;
import com.prishtha.mvp.tenant.internal.repository.TenantRepository;
import com.prishtha.mvp.tenant.internal.repository.TenantThemeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;
    private final TenantDomainRepository tenantDomainRepository;
    private final TenantThemeRepository tenantThemeRepository;

    @Override
    public boolean existsById(Long tenantId) {
        if (tenantId == null) {
            return false;
        }
        return tenantRepository.existsById(tenantId);
    }

    @Override
    public Optional<Long> resolveTenantIdByDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return Optional.empty();
        }
        return tenantDomainRepository.findByCustomDomainAndIsActiveTrue(domain)
                .map(tenantDomain -> tenantDomain.getTenant().getId());
    }

    @Override
    public TenantThemeResponseDto getThemeByDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            throw new IllegalArgumentException("Domain cannot be empty");
        }
        TenantDomain tenantDomain = tenantDomainRepository.findByCustomDomainAndIsActiveTrue(domain)
                .orElseThrow(() -> new IllegalArgumentException("Active tenant not found for domain: " + domain));

        Long tenantId = tenantDomain.getTenant().getId();
        TenantTheme theme = tenantThemeRepository.findByTenantId(tenantId)
                .orElse(null);

        if (theme == null) {
            return TenantThemeResponseDto.builder()
                    .brandLogoUrl(null)
                    .primaryColor("#000000")
                    .secondaryColor("#FFFFFF")
                    .customStylesheetUrl(null)
                    .build();
        }

        return TenantThemeResponseDto.builder()
                .brandLogoUrl(theme.getBrandLogoUrl())
                .primaryColor(theme.getPrimaryColor())
                .secondaryColor(theme.getSecondaryColor())
                .customStylesheetUrl(theme.getCustomStylesheetUrl())
                .build();
    }
}
