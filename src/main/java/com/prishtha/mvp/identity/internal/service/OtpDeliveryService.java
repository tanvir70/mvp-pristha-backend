package com.prishtha.mvp.identity.internal.service;

interface OtpDeliveryService {
    void send(String phone, String code);
}
