package com.prishtha.mvp.identity.internal.repository;

import com.prishtha.mvp.identity.internal.entity.SecurityAuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, Long> {
    List<SecurityAuditLog> findTop50ByUser_IdOrderByCreatedAtDesc(Long userId);

    void deleteByUser_Id(Long userId);
}
