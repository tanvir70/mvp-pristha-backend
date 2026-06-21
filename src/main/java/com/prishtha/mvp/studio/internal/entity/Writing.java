package com.prishtha.mvp.studio.internal.entity;

import com.prishtha.mvp.shared.entity.BaseEntity;
import com.prishtha.mvp.studio.internal.enums.PriceType;
import com.prishtha.mvp.studio.internal.enums.WritingStatus;
import com.prishtha.mvp.studio.internal.enums.WritingType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "writings", schema = "studio")
@Getter
@Setter
public class Writing extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Writing parent;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "slug", length = 280)
    private String slug;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "body_json", columnDefinition = "jsonb")
    private String bodyJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preview_json", columnDefinition = "jsonb")
    private String previewJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private WritingType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WritingStatus status = WritingStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_type", nullable = false, length = 20)
    private PriceType priceType = PriceType.FREE;

    @Column(name = "price_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAmount = BigDecimal.ZERO;

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
