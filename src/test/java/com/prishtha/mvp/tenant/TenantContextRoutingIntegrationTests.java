package com.prishtha.mvp.tenant;

import com.prishtha.mvp.shared.context.TenantContext;
import com.prishtha.mvp.tenant.internal.entity.Tenant;
import com.prishtha.mvp.tenant.internal.entity.TenantDomain;
import com.prishtha.mvp.tenant.internal.entity.TenantTheme;
import com.prishtha.mvp.tenant.internal.repository.TenantDomainRepository;
import com.prishtha.mvp.tenant.internal.repository.TenantRepository;
import com.prishtha.mvp.tenant.internal.repository.TenantThemeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TenantContextRoutingIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantDomainRepository tenantDomainRepository;

    @Autowired
    private TenantThemeRepository tenantThemeRepository;

    private Tenant tenant1;
    private Tenant tenant2;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        // Provision Tenant 1
        tenant1 = new Tenant();
        tenant1.setName("Tenant One");
        tenant1 = tenantRepository.save(tenant1);

        TenantDomain domain1 = new TenantDomain();
        domain1.setTenant(tenant1);
        domain1.setCustomDomain("tenant1.pristha.com");
        domain1.setActive(true);
        tenantDomainRepository.save(domain1);

        TenantTheme theme1 = new TenantTheme();
        theme1.setTenant(tenant1);
        theme1.setBrandLogoUrl("https://tenant1.com/logo.png");
        theme1.setPrimaryColor("#FF0000");
        theme1.setSecondaryColor("#00FF00");
        theme1.setCustomStylesheetUrl("https://tenant1.com/style.css");
        tenantThemeRepository.save(theme1);

        // Provision Tenant 2
        tenant2 = new Tenant();
        tenant2.setName("Tenant Two");
        tenant2 = tenantRepository.save(tenant2);

        TenantDomain domain2 = new TenantDomain();
        domain2.setTenant(tenant2);
        domain2.setCustomDomain("tenant2.pristha.com");
        domain2.setActive(true);
        tenantDomainRepository.save(domain2);
    }

    @Test
    void testTenantContextSetWhenValidHostHeaderProvided() throws Exception {
        mockMvc.perform(get("/api/v1/auth/test-tenant-routing")
                        .header("Host", "tenant1.pristha.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(tenant1.getId().toString()));

        assertThat(TenantContext.getCurrentTenantId()).isNull();
    }

    @Test
    void testTenantContextSetWhenValidXForwardedHostHeaderProvided() throws Exception {
        mockMvc.perform(get("/api/v1/auth/test-tenant-routing")
                        .header("X-Forwarded-Host", "tenant2.pristha.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(tenant2.getId().toString()));

        assertThat(TenantContext.getCurrentTenantId()).isNull();
    }

    @Test
    void testTenantContextNullWhenUnknownHostHeaderProvided() throws Exception {
        mockMvc.perform(get("/api/v1/auth/test-tenant-routing")
                        .header("Host", "unknown.domain.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        assertThat(TenantContext.getCurrentTenantId()).isNull();
    }

    @Test
    void testTenantContextNullWhenNoHostHeaderProvided() throws Exception {
        mockMvc.perform(get("/api/v1/auth/test-tenant-routing"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        assertThat(TenantContext.getCurrentTenantId()).isNull();
    }

    @Test
    void testTenantContextHandlingOfHostWithPort() throws Exception {
        mockMvc.perform(get("/api/v1/auth/test-tenant-routing")
                        .header("Host", "tenant1.pristha.com:8080"))
                .andExpect(status().isOk())
                .andExpect(content().string(tenant1.getId().toString()));

        assertThat(TenantContext.getCurrentTenantId()).isNull();
    }
}

@RestController
class TestTenantRoutingController {

    @GetMapping("/api/v1/auth/test-tenant-routing")
    public ResponseEntity<Long> getTenantId() {
        return ResponseEntity.ok(TenantContext.getCurrentTenantId());
    }
}
