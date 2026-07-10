package com.prishtha.mvp.catalog.internal.repository;

import com.prishtha.mvp.catalog.internal.entity.PublishedWriting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

public interface PublishedWritingRepository extends JpaRepository<PublishedWriting, Long> {
    Optional<PublishedWriting> findBySlug(String slug);

    Page<PublishedWriting> findByAuthorId(Long authorId, Pageable pageable);

    Page<PublishedWriting> findAllByOrderByPublishedAtDesc(Pageable pageable);

    @Query(
            value = """
                    SELECT * FROM catalog.published_writings pw
                    WHERE pw.search_tsv @@ plainto_tsquery('simple', :query)
                    ORDER BY pw.published_at DESC
                    """,
            countQuery = """
                    SELECT count(*) FROM catalog.published_writings pw
                    WHERE pw.search_tsv @@ plainto_tsquery('simple', :query)
                    """,
            nativeQuery = true)
    Page<PublishedWriting> search(@Param("query") String query, Pageable pageable);

    @Query("""
            SELECT pw FROM PublishedWriting pw
            JOIN PublishedWritingTag pwt ON pwt.writing.id = pw.id
            WHERE pwt.id.tag = :tag
            ORDER BY pw.publishedAt DESC
            """)
    Page<PublishedWriting> findByTag(@Param("tag") String tag, Pageable pageable);

    @Modifying
    @Query("UPDATE PublishedWriting pw SET pw.viewCount = pw.viewCount + 1 WHERE pw.id = :id")
    void incrementViewCount(@Param("id") Long id);
}
