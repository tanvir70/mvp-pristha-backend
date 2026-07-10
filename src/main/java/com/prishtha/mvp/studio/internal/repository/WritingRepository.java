package com.prishtha.mvp.studio.internal.repository;

import com.prishtha.mvp.studio.internal.entity.Writing;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WritingRepository extends JpaRepository<Writing, Long> {
    Optional<Writing> findBySlug(String slug);

    List<Writing> findByAuthorIdAndDeletedAtIsNull(Long authorId);

    List<Writing> findByParentIdOrderByOrderIndexAsc(Long parentId);

    Page<Writing> findByAuthorIdAndDeletedAtIsNullOrderByUpdatedAtDesc(Long authorId, Pageable pageable);

    Optional<Writing> findByIdAndAuthorIdAndDeletedAtIsNull(Long id, Long authorId);

    boolean existsBySlug(String slug);
}
