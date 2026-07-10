package com.prishtha.mvp.catalog.internal.entity;

import com.prishtha.mvp.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "writing_media", schema = "catalog")
@Getter
@Setter
public class WritingMedia extends BaseEntity {

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, length = 80)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private int fileSizeBytes;
}
