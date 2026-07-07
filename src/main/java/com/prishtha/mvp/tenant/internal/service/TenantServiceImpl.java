package com.prishtha.mvp.tenant.internal.service;

import com.prishtha.mvp.tenant.api.contract.TenantService;
import com.prishtha.mvp.tenant.internal.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;

    @Override
    public boolean existsById(Long tenantId) {
        if (tenantId == null) {
            return false;
        }
        return tenantRepository.existsById(tenantId);
    }
}
