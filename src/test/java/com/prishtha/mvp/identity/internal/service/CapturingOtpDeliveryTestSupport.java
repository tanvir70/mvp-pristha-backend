package com.prishtha.mvp.identity.internal.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Package matches internal.service (not api) so tests can reach the
// package-private OtpDeliveryService to capture what would otherwise
// leave the module as an SMS send.
@TestConfiguration
class CapturingOtpDeliveryTestSupport {

    @Bean
    @Primary
    OtpDeliveryService capturingOtpDeliveryService() {
        return new CapturingOtpDeliveryService();
    }

    static class CapturingOtpDeliveryService implements OtpDeliveryService {
        private final Map<String, String> lastCodeByPhone = new ConcurrentHashMap<>();

        @Override
        public void send(String phone, String code) {
            lastCodeByPhone.put(phone, code);
        }

        String lastCode(String phone) {
            return lastCodeByPhone.get(phone);
        }
    }
}
