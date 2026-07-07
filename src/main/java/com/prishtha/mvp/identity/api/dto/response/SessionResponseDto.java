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
public class SessionResponseDto {
    private Long id;
    private String deviceLabel;
    private String ipAddress;
    private String userAgent;
    private Instant createdAt;
    private Instant lastUsedAt;
}
