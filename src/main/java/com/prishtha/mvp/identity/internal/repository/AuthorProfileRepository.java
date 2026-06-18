package com.prishtha.mvp.identity.internal.repository;

import com.prishtha.mvp.identity.internal.entity.AuthorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AuthorProfileRepository extends JpaRepository<AuthorProfile, Long> {
    Optional<AuthorProfile> findByPenName(String penName);
}
