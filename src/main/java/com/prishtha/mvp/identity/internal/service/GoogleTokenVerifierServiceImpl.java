package com.prishtha.mvp.identity.internal.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.prishtha.mvp.shared.exception.AuthenticationFailedException;
import java.security.GeneralSecurityException;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class GoogleTokenVerifierServiceImpl implements GoogleTokenVerifierService {

    private static final String INVALID_GOOGLE_TOKEN_MESSAGE = "Invalid Google ID token";

    private final GoogleIdTokenVerifier googleIdTokenVerifier;

    @Override
    public GoogleUserInfo verify(String idToken) {
        GoogleIdToken token;
        try {
            token = googleIdTokenVerifier.verify(idToken);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            throw new AuthenticationFailedException(INVALID_GOOGLE_TOKEN_MESSAGE);
        }
        if (token == null) {
            throw new AuthenticationFailedException(INVALID_GOOGLE_TOKEN_MESSAGE);
        }

        GoogleIdToken.Payload payload = token.getPayload();
        // Login links accounts by email (AuthServiceImpl.loginWithGoogle), so an
        // unverified email would let an attacker claim someone else's account.
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new AuthenticationFailedException(INVALID_GOOGLE_TOKEN_MESSAGE);
        }
        return new GoogleUserInfo(
                payload.getSubject(),
                payload.getEmail(),
                (String) payload.get("name"),
                (String) payload.get("picture"));
    }
}
