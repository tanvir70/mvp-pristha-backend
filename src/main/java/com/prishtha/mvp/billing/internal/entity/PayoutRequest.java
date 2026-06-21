package com.prishtha.mvp.billing.internal.entity;

import com.prishtha.mvp.billing.internal.enums.PayoutStatus;
import com.prishtha.mvp.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "payout_requests", schema = "billing")
@Getter
@Setter
public class PayoutRequest extends BaseEntity {

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PayoutStatus status = PayoutStatus.PENDING;

    @Column(name = "payout_mfs_number", nullable = false, length = 20)
    private String payoutMfsNumber;
}
