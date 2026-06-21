package com.prishtha.mvp.catalog.internal.repository;

import com.prishtha.mvp.catalog.internal.entity.Follow;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    Optional<Follow> findByFollowerIdAndAuthorId(Long followerId, Long authorId);

    List<Follow> findByFollowerId(Long followerId);

    List<Follow> findByAuthorId(Long authorId);
}
