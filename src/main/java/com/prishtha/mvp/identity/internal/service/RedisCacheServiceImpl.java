package com.prishtha.mvp.identity.internal.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class RedisCacheServiceImpl implements CacheService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void storeRefreshToken(Long userId, String tokenId, Duration ttl) {
        redisTemplate.opsForValue().set(refreshKey(userId, tokenId), "1", ttl);
    }

    @Override
    public boolean existsRefreshToken(Long userId, String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(refreshKey(userId, tokenId)));
    }

    @Override
    public void deleteRefreshToken(Long userId, String tokenId) {
        redisTemplate.delete(refreshKey(userId, tokenId));
    }

    @Override
    public long incrementLoginAttempts(String phone, Duration window) {
        String key = loginAttemptsKey(phone);
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(key, window);
        }
        return attempts == null ? 0 : attempts;
    }

    @Override
    public void resetLoginAttempts(String phone) {
        redisTemplate.delete(loginAttemptsKey(phone));
    }

    @Override
    public void lockLogin(String phone, Duration lockTtl) {
        redisTemplate.opsForValue().set(loginLockKey(phone), "1", lockTtl);
    }

    @Override
    public boolean isLoginLocked(String phone) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(loginLockKey(phone)));
    }

    private String refreshKey(Long userId, String tokenId) {
        return "refresh:" + userId + ":" + tokenId;
    }

    private String loginAttemptsKey(String phone) {
        return "login_attempts:" + phone;
    }

    private String loginLockKey(String phone) {
        return "login_lock:" + phone;
    }
}
