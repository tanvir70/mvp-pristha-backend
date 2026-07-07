package com.prishtha.mvp.identity.internal.service;

import com.prishtha.mvp.identity.internal.config.AuthProperties;
import com.prishtha.mvp.identity.internal.enums.OtpPurpose;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class OtpServiceImpl implements OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CacheService cacheService;
    private final OtpDeliveryService otpDeliveryService;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    @Override
    public void sendOtp(String phone, OtpPurpose purpose) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        cacheService.storeOtp(phone, purpose.name(), passwordEncoder.encode(code), authProperties.otpTtl());
        otpDeliveryService.send(phone, code);
    }

    @Override
    public boolean verifyOtp(String phone, OtpPurpose purpose, String code) {
        String hash = cacheService.getOtpHash(phone, purpose.name()).orElse(null);
        if (hash == null) {
            return false;
        }

        if (passwordEncoder.matches(code, hash)) {
            cacheService.deleteOtp(phone, purpose.name());
            return true;
        }

        long attempts = cacheService.incrementOtpAttempts(phone, purpose.name(), authProperties.otpTtl());
        if (attempts >= authProperties.otpMaxAttempts()) {
            cacheService.deleteOtp(phone, purpose.name());
        }
        return false;
    }
}
