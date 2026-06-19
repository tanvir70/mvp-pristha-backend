package com.prishtha.mvp.catalog.internal.entity;

import com.prishtha.mvp.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tags", schema = "catalog")
@Getter
@Setter
public class Tag extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 60)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 70)
    private String slug;
}
