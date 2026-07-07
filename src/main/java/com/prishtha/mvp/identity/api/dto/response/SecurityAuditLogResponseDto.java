package com.prishtha.mvp.identity.api.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAuditLogResponseDto {
    private Long id;
    private String eventType;
    private String ipAddress;
    private String userAgent;
    private String detail;
    private Instant createdAt;
}
