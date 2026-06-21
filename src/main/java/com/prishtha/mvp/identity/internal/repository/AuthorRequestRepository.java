package com.prishtha.mvp.identity.internal.repository;

import com.prishtha.mvp.identity.internal.entity.AuthorRequest;
import com.prishtha.mvp.identity.internal.entity.AuthorRequestStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorRequestRepository extends JpaRepository<AuthorRequest, Long> {

    boolean existsByUser_IdAndStatus(Long userId, AuthorRequestStatus status);

    Optional<AuthorRequest> findFirstByUser_IdOrderByCreatedAtDesc(Long userId);

    List<AuthorRequest> findByStatusOrderByCreatedAtAsc(AuthorRequestStatus status);
}
