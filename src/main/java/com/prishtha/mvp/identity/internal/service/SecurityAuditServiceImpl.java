package com.prishtha.mvp.identity.internal.service;

import com.prishtha.mvp.identity.internal.entity.SecurityAuditLog;
import com.prishtha.mvp.identity.internal.entity.User;
import com.prishtha.mvp.identity.internal.enums.SecurityEventType;
import com.prishtha.mvp.identity.internal.repository.SecurityAuditLogRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class SecurityAuditServiceImpl implements SecurityAuditService {

    private final SecurityAuditLogRepository securityAuditLogRepository;

    // REQUIRES_NEW so an audit entry for a failed/rejected auth attempt
    // survives even though the caller's transaction rolls back on exception.
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(User user, String phone, SecurityEventType eventType, String ipAddress, String userAgent, String detail) {
        SecurityAuditLog log = new SecurityAuditLog();
        log.setUser(user);
        log.setPhone(phone);
        log.setEventType(eventType);
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);
        log.setDetail(detail);
        securityAuditLogRepository.save(log);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SecurityAuditLog> recentForUser(Long userId) {
        return securityAuditLogRepository.findTop50ByUser_IdOrderByCreatedAtDesc(userId);
    }
}
