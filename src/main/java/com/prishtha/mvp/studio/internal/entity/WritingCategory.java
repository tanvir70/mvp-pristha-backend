package com.prishtha.mvp.studio.internal.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "writing_categories", schema = "studio")
@Getter
@Setter
public class WritingCategory {

    @EmbeddedId
    private WritingCategoryId id = new WritingCategoryId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("writingId")
    @JoinColumn(name = "writing_id", nullable = false)
    private Writing writing;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("categoryId")
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
}
