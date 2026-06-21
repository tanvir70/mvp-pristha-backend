package com.prishtha.mvp.social.internal.repository;

import com.prishtha.mvp.social.internal.entity.WritingComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WritingCommentRepository extends JpaRepository<WritingComment, Long> {
    List<WritingComment> findByWritingIdAndDeletedAtIsNullOrderByCreatedAtAsc(Long writingId);

    long countByWritingIdAndDeletedAtIsNull(Long writingId);
}
