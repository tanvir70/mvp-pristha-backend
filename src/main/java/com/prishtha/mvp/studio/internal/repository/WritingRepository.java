package com.prishtha.mvp.studio.internal.repository;

import com.prishtha.mvp.studio.internal.entity.Writing;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WritingRepository extends JpaRepository<Writing, Long> {
    Optional<Writing> findBySlug(String slug);

    List<Writing> findByAuthorIdAndDeletedAtIsNull(Long authorId);

    List<Writing> findByParentIdOrderByOrderIndexAsc(Long parentId);
}
