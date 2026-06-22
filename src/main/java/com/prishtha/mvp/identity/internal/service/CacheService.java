package com.prishtha.mvp.identity.internal.service;

import java.time.Duration;

interface CacheService {
    void storeRefreshToken(Long userId, String tokenId, Duration ttl);
    boolean existsRefreshToken(Long userId, String tokenId);
    void deleteRefreshToken(Long userId, String tokenId);

    long incrementLoginAttempts(String phone, Duration window);
    void resetLoginAttempts(String phone);
    void lockLogin(String phone, Duration lockTtl);
    boolean isLoginLocked(String phone);
}
