package com.prishtha.mvp.social.internal.repository;

import com.prishtha.mvp.social.internal.entity.WritingLike;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WritingLikeRepository extends JpaRepository<WritingLike, Long> {
    Optional<WritingLike> findByWritingIdAndUserId(Long writingId, Long userId);

    boolean existsByWritingIdAndUserId(Long writingId, Long userId);

    long countByWritingId(Long writingId);
}
