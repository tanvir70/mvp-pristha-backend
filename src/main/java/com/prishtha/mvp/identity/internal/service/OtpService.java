package com.prishtha.mvp.identity.internal.service;

import com.prishtha.mvp.identity.internal.enums.OtpPurpose;

interface OtpService {
    void sendOtp(String phone, OtpPurpose purpose);
    boolean verifyOtp(String phone, OtpPurpose purpose, String code);
}
