package com.prishtha.mvp.identity.internal.repository;

import com.prishtha.mvp.identity.internal.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPhone(String phone);
}
