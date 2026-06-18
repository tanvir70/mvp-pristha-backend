package com.prishtha.mvp.tenant.internal.repository;

import com.prishtha.mvp.tenant.internal.entity.TenantTheme;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TenantThemeRepository extends JpaRepository<TenantTheme, Long> {
    Optional<TenantTheme> findByTenantId(Long tenantId);
}
