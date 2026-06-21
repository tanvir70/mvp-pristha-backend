package com.prishtha.mvp.catalog.internal.repository;

import com.prishtha.mvp.catalog.internal.entity.PublishedWriting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PublishedWritingRepository extends JpaRepository<PublishedWriting, Long> {
    Optional<PublishedWriting> findBySlug(String slug);

    Page<PublishedWriting> findByAuthorId(Long authorId, Pageable pageable);

    Page<PublishedWriting> findAllByOrderByPublishedAtDesc(Pageable pageable);
}
