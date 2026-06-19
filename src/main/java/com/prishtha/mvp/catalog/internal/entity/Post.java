package com.prishtha.mvp.catalog.internal.entity;

import com.prishtha.mvp.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "posts", schema = "catalog")
@Getter
@Setter
public class Post extends BaseEntity {

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "slug", nullable = false, unique = true, length = 280)
    private String slug;

    @Column(name = "excerpt", length = 500)
    private String excerpt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "body", nullable = false, columnDefinition = "jsonb")
    private String body;

    @Column(name = "body_plain_text", columnDefinition = "TEXT")
    private String bodyPlainText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preview_body", columnDefinition = "jsonb")
    private String previewBody;

    @Column(name = "cover_image_url", length = 512)
    private String coverImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_type", nullable = false, length = 20)
    private PricingType pricingType = PricingType.FREE;

    @Column(name = "price_amount", precision = 10, scale = 2)
    private BigDecimal priceAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PostStatus status = PostStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "view_count", nullable = false)
    private long viewCount = 0;

    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    @Column(name = "comment_count", nullable = false)
    private int commentCount = 0;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
