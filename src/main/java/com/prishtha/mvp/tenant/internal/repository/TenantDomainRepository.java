package com.prishtha.mvp.tenant.internal.repository;

import com.prishtha.mvp.tenant.internal.entity.TenantDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TenantDomainRepository extends JpaRepository<TenantDomain, Long> {
    Optional<TenantDomain> findByCustomDomainAndIsActiveTrue(String customDomain);
}
