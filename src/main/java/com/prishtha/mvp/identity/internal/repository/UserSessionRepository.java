package com.prishtha.mvp.identity.internal.repository;

import com.prishtha.mvp.identity.internal.entity.UserSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    List<UserSession> findByUser_IdAndRevokedFalseOrderByLastUsedAtDesc(Long userId);
    Optional<UserSession> findByUser_IdAndRefreshTokenId(Long userId, String refreshTokenId);
    List<UserSession> findByUser_IdAndRevokedFalse(Long userId);
}
