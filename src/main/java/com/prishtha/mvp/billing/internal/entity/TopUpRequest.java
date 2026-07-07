package com.prishtha.mvp.billing.internal.entity;

import com.prishtha.mvp.billing.internal.enums.TopUpStatus;
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
@Table(name = "top_up_requests", schema = "billing")
@Getter
@Setter
public class TopUpRequest extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TopUpStatus status = TopUpStatus.PENDING;

    @Column(name = "gateway_ref", length = 255)
    private String gatewayRef;
}
