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
public class AuthorRequestResponseDto {
    private Long id;
    private Long userId;
    private String requestedPenName;
    private String motivation;
    private String sampleWritingUrl;
    private String status;
    private Long reviewedBy;
    private String reviewNote;
    private Instant reviewedAt;
    private Instant createdAt;
}
