package com.prishtha.mvp.identity.internal.service;

import java.time.Duration;
import java.util.Optional;

interface CacheService {
    void storeRefreshToken(Long userId, String tokenId, Duration ttl);
    boolean existsRefreshToken(Long userId, String tokenId);
    void deleteRefreshToken(Long userId, String tokenId);
    void deleteAllRefreshTokens(Long userId);

    long incrementLoginAttempts(String phone, Duration window);
    void resetLoginAttempts(String phone);
    void lockLogin(String phone, Duration lockTtl);
    boolean isLoginLocked(String phone);

    void storeOtp(String phone, String purpose, String codeHash, Duration ttl);
    Optional<String> getOtpHash(String phone, String purpose);
    void deleteOtp(String phone, String purpose);
    long incrementOtpAttempts(String phone, String purpose, Duration ttl);

    void storeMfaChallenge(String challengeToken, Long userId, Duration ttl);
    Optional<Long> getMfaChallengeUserId(String challengeToken);
    void deleteMfaChallenge(String challengeToken);

    long incrementRateLimit(String bucketKey, Duration window);
}
