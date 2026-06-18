package com.prishtha.mvp.identity.internal.entity;

import com.prishtha.mvp.shared.entity.BaseEntity;
import jakarta.persistence.*;
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

    @Column(name = "pen_name", nullable = false, unique = true, length = 100)
    private String penName;

    @Column(name = "biography", columnDefinition = "TEXT")
    private String biography;

    @Column(name = "payout_mfs_number", nullable = false, length = 20)
    private String payoutMfsNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_mfs_provider", nullable = false, length = 20)
    private MfsProvider payoutMfsProvider;
}
