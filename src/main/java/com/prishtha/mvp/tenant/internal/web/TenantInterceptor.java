package com.prishtha.mvp.tenant.internal.web;

import com.prishtha.mvp.shared.context.TenantContext;
import com.prishtha.mvp.tenant.api.contract.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantInterceptor implements HandlerInterceptor {

    private final TenantService tenantService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String rawHost = request.getHeader("X-Forwarded-Host");
        if (rawHost == null || rawHost.isBlank()) {
            rawHost = request.getHeader("Host");
        }

        if (rawHost != null) {
            int colonIndex = rawHost.indexOf(':');
            if (colonIndex != -1) {
                rawHost = rawHost.substring(0, colonIndex);
            }
            final String host = rawHost.trim();

            tenantService.resolveTenantIdByDomain(host).ifPresentOrElse(
                tenantId -> {
                    log.debug("Resolved tenant ID {} for host {}", tenantId, host);
                    TenantContext.setCurrentTenantId(tenantId);
                },
                () -> {
                    log.debug("No tenant found for host {}", host);
                    TenantContext.setCurrentTenantId(null);
                }
            );
        } else {
            TenantContext.setCurrentTenantId(null);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        TenantContext.clear();
    }
}
