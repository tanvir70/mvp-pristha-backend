package com.prishtha.mvp.identity.internal.entity;

import com.prishtha.mvp.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "author_profiles", schema = "identity")
@Getter
@Setter
public class AuthorProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "pen_name", unique = true, length = 80)
    private String penName;

    @Column(name = "biography", columnDefinition = "TEXT")
    private String biography;

    @Column(name = "payout_phone", length = 15)
    private String payoutPhone;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
