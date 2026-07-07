package com.prishtha.mvp.identity.internal.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity.auth")
public record AuthProperties(
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration loginAttemptsWindow,
        Duration loginLockTtl,
        int maxLoginAttempts,
        Duration otpTtl,
        int otpMaxAttempts,
        Duration mfaChallengeTtl,
        Duration rateLimitWindow,
        int rateLimitMaxRequests) {
}
