package com.prishtha.mvp.identity.internal.service;

import com.prishtha.mvp.identity.internal.entity.SecurityAuditLog;
import com.prishtha.mvp.identity.internal.entity.User;
import com.prishtha.mvp.identity.internal.enums.SecurityEventType;
import java.util.List;

interface SecurityAuditService {
    void record(User user, String phone, SecurityEventType eventType, String ipAddress, String userAgent, String detail);
    List<SecurityAuditLog> recentForUser(Long userId);
}
