package com.prishtha.mvp.catalog.internal.repository;

import com.prishtha.mvp.catalog.internal.entity.Post;
import com.prishtha.mvp.catalog.internal.entity.PostStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByStatusAndDeletedAtIsNullOrderByPublishedAtDesc(PostStatus status, Pageable pageable);

    Optional<Post> findBySlugAndStatusAndDeletedAtIsNull(String slug, PostStatus status);

    Optional<Post> findByIdAndAuthorIdAndDeletedAtIsNull(Long id, Long authorId);

    Page<Post> findByAuthorIdAndDeletedAtIsNullOrderByUpdatedAtDesc(Long authorId, Pageable pageable);

    boolean existsBySlug(String slug);

    @Query("""
            SELECT p
            FROM Post p
            WHERE p.status = :status
              AND p.deletedAt IS NULL
              AND (
                    LOWER(COALESCE(p.title, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                 OR LOWER(COALESCE(p.excerpt, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                 OR LOWER(COALESCE(p.bodyPlainText, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            ORDER BY p.publishedAt DESC
            """)
    Page<Post> searchPublishedPosts(
            @Param("status") PostStatus status, @Param("query") String query, Pageable pageable);

    @Query("""
            SELECT DISTINCT p
            FROM Post p
            JOIN PostTag pt ON pt.post.id = p.id
            JOIN Tag t ON t.id = pt.tag.id
            WHERE p.status = :status
              AND p.deletedAt IS NULL
              AND t.slug = :tagSlug
            ORDER BY p.publishedAt DESC
            """)
    Page<Post> findPublishedPostsByTagSlug(
            @Param("status") PostStatus status, @Param("tagSlug") String tagSlug, Pageable pageable);
}
