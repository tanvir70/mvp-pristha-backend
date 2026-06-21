package com.prishtha.mvp.catalog.internal.entity;

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
@Table(name = "published_writing_tags", schema = "catalog")
@Getter
@Setter
public class PublishedWritingTag {

    @EmbeddedId
    private PublishedWritingTagId id = new PublishedWritingTagId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("writingId")
    @JoinColumn(name = "writing_id", nullable = false)
    private PublishedWriting writing;
}
