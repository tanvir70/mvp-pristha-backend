package com.prishtha.mvp.analytics.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "content_unlocks", schema = "analytics")
@Getter
@Setter
public class ContentUnlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "writing_id", nullable = false)
    private Long writingId;

    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    @Column(name = "unlocked_at", nullable = false, updatable = false)
    private Instant unlockedAt;

    @PrePersist
    protected void onCreate() {
        unlockedAt = Instant.now();
    }
}
