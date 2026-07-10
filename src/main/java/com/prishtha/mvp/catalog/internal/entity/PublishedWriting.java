package com.prishtha.mvp.catalog.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "published_writings", schema = "catalog")
@Getter
@Setter
public class PublishedWriting {

    // Assigned = studio.writings.id at publish time. Not auto-generated.
    @Id
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "author_pen_name", nullable = false, length = 100)
    private String authorPenName;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "slug", nullable = false, unique = true, length = 280)
    private String slug;

    @Column(name = "synopsis", columnDefinition = "TEXT")
    private String synopsis;

    @Column(name = "cover_image_url", length = 512)
    private String coverImageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preview_json", columnDefinition = "jsonb")
    private String previewJson;

    @Column(name = "type", nullable = false, length = 30)
    private String type;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "price_type", nullable = false, length = 20)
    private String priceType;

    @Column(name = "price_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAmount;

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    @Column(name = "like_count", nullable = false)
    private long likeCount = 0;

    @Column(name = "comment_count", nullable = false)
    private long commentCount = 0;

    @Column(name = "view_count", nullable = false)
    private long viewCount = 0;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;
}
