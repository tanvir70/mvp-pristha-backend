package com.prishtha.mvp.identity.internal.service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
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

    // ponytail: KEYS is O(n) over the keyspace — fine at this scale. Upgrade to
    // a per-user SET of live tokenIds (SADD on issue, SMEMBERS+DEL here) if the
    // refresh-token keyspace grows large enough for KEYS to matter.
    @Override
    public void deleteAllRefreshTokens(Long userId) {
        Set<String> keys = redisTemplate.keys("refresh:" + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
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

    @Override
    public void storeOtp(String phone, String purpose, String codeHash, Duration ttl) {
        redisTemplate.opsForValue().set(otpKey(phone, purpose), codeHash, ttl);
    }

    @Override
    public Optional<String> getOtpHash(String phone, String purpose) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(otpKey(phone, purpose)));
    }

    @Override
    public void deleteOtp(String phone, String purpose) {
        redisTemplate.delete(otpKey(phone, purpose));
        redisTemplate.delete(otpAttemptsKey(phone, purpose));
    }

    @Override
    public long incrementOtpAttempts(String phone, String purpose, Duration ttl) {
        String key = otpAttemptsKey(phone, purpose);
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(key, ttl);
        }
        return attempts == null ? 0 : attempts;
    }

    @Override
    public void storeMfaChallenge(String challengeToken, Long userId, Duration ttl) {
        redisTemplate.opsForValue().set(mfaChallengeKey(challengeToken), userId.toString(), ttl);
    }

    @Override
    public Optional<Long> getMfaChallengeUserId(String challengeToken) {
        String value = redisTemplate.opsForValue().get(mfaChallengeKey(challengeToken));
        return Optional.ofNullable(value).map(Long::parseLong);
    }

    @Override
    public void deleteMfaChallenge(String challengeToken) {
        redisTemplate.delete(mfaChallengeKey(challengeToken));
    }

    @Override
    public long incrementRateLimit(String bucketKey, Duration window) {
        String key = "ratelimit:" + bucketKey;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }
        return count == null ? 0 : count;
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

    private String otpKey(String phone, String purpose) {
        return "otp:" + purpose + ":" + phone;
    }

    private String otpAttemptsKey(String phone, String purpose) {
        return "otp_attempts:" + purpose + ":" + phone;
    }

    private String mfaChallengeKey(String challengeToken) {
        return "mfa_challenge:" + challengeToken;
    }
}
