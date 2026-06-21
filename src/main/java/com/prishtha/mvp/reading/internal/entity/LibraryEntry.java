package com.prishtha.mvp.reading.internal.entity;

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
@Table(name = "library_entries", schema = "reading")
@Getter
@Setter
public class LibraryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    @Column(name = "writing_id", nullable = false)
    private Long writingId;

    @Column(name = "last_read_chapter_id")
    private Long lastReadChapterId;

    @Column(name = "last_read_page_num", nullable = false)
    private int lastReadPageNum = 1;

    @Column(name = "last_read_at", nullable = false)
    private Instant lastReadAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        if (lastReadAt == null) {
            lastReadAt = now;
        }
    }
}
