package com.prishtha.mvp.tenant.internal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "tenant_themes", schema = "tenant")
@Getter
@Setter
public class TenantTheme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;

    @Column(name = "brand_logo_url", length = 512)
    private String brandLogoUrl;

    @Column(name = "primary_color", length = 10)
    private String primaryColor = "#000000";

    @Column(name = "secondary_color", length = 10)
    private String secondaryColor = "#FFFFFF";

    @Column(name = "custom_stylesheet_url", length = 512)
    private String customStylesheetUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
