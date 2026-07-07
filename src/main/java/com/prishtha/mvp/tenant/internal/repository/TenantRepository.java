package com.prishtha.mvp.tenant.internal.repository;

import com.prishtha.mvp.tenant.internal.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
}
