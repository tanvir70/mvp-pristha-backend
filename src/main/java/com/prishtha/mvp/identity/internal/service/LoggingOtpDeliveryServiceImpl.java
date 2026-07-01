package com.prishtha.mvp.identity.internal.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// ponytail: logs the OTP instead of sending an SMS — no SMS gateway
// (Twilio/SNS/local provider) is wired up yet. Upgrade: implement this
// interface against the chosen gateway and swap the @Service bean.
@Slf4j
@Service
class LoggingOtpDeliveryServiceImpl implements OtpDeliveryService {

    @Override
    public void send(String phone, String code) {
        log.info("OTP for {}: {}", phone, code);
    }
}
