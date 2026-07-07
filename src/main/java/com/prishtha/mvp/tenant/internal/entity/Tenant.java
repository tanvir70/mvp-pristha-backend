package com.prishtha.mvp.tenant.internal.entity;

import com.prishtha.mvp.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tenants", schema = "tenant")
@Getter
@Setter
public class Tenant extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;
}
