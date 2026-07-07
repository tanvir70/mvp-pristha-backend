package com.prishtha.mvp.identity.internal.service;

interface GoogleTokenVerifierService {
    GoogleUserInfo verify(String idToken);
}
