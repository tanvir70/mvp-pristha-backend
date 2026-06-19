package com.prishtha.mvp.reading.internal.entity;

import com.prishtha.mvp.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "reading_progress",
        schema = "reading",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "post_id"}))
@Getter
@Setter
public class ReadingProgress extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "scroll_position", nullable = false)
    private int scrollPosition = 0;

    @Column(name = "progress_percent", nullable = false)
    private short progressPercent = 0;

    @Column(name = "last_read_at", nullable = false)
    private Instant lastReadAt;

    @PrePersist
    protected void onReadingProgressCreate() {
        if (lastReadAt == null) {
            lastReadAt = Instant.now();
        }
    }
}
