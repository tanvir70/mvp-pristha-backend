package com.prishtha.mvp.identity.internal.service;

import com.prishtha.mvp.identity.api.contract.AuthService;
import com.prishtha.mvp.identity.api.dto.request.ChangePasswordRequestDto;
import com.prishtha.mvp.identity.api.dto.request.ForgotPasswordRequestDto;
import com.prishtha.mvp.identity.api.dto.request.LoginRequestDto;
import com.prishtha.mvp.identity.api.dto.request.LogoutRequestDto;
import com.prishtha.mvp.identity.api.dto.request.MfaVerifyRequestDto;
import com.prishtha.mvp.identity.api.dto.request.RefreshTokenRequestDto;
import com.prishtha.mvp.identity.api.dto.request.ResetPasswordRequestDto;
import com.prishtha.mvp.identity.api.dto.request.SocialLoginRequestDto;
import com.prishtha.mvp.identity.api.dto.response.AuthTokenResponseDto;
import com.prishtha.mvp.identity.api.dto.response.SecurityAuditLogResponseDto;
import com.prishtha.mvp.identity.api.dto.response.SessionResponseDto;
import com.prishtha.mvp.identity.internal.config.AuthProperties;
import com.prishtha.mvp.identity.internal.entity.AuthorProfile;
import com.prishtha.mvp.identity.internal.entity.User;
import com.prishtha.mvp.identity.internal.entity.UserSession;
import com.prishtha.mvp.identity.internal.enums.OtpPurpose;
import com.prishtha.mvp.identity.internal.enums.SecurityEventType;
import com.prishtha.mvp.identity.internal.enums.UserStatus;
import com.prishtha.mvp.identity.internal.repository.AuthorProfileRepository;
import com.prishtha.mvp.identity.internal.repository.UserRepository;
import com.prishtha.mvp.identity.internal.repository.UserSessionRepository;
import com.prishtha.mvp.shared.exception.AuthenticationFailedException;
import com.prishtha.mvp.shared.exception.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Transactional
class AuthServiceImpl implements AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid phone number or password";
    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid or expired refresh token";
    private static final String INVALID_MFA_CHALLENGE_MESSAGE = "Invalid or expired MFA challenge";
    private static final String INVALID_MFA_CODE_MESSAGE = "Invalid or expired MFA code";
    private static final String INVALID_OTP_MESSAGE = "Invalid or expired verification code";

    private final UserRepository userRepository;
    private final AuthorProfileRepository authorProfileRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final CacheService cacheService;
    private final AuthProperties authProperties;
    private final OtpService otpService;
    private final GoogleTokenVerifierService googleTokenVerifierService;
    private final SecurityAuditService securityAuditService;
    private final HttpServletRequest httpServletRequest;
    private final PlatformTransactionManager transactionManager;

    @Override
    public AuthTokenResponseDto login(LoginRequestDto requestDto) {
        String phone = requestDto.getPhone();

        if (cacheService.isLoginLocked(phone)) {
            throw new AuthenticationFailedException(
                    "Account temporarily locked due to repeated failed login attempts");
        }

        User user = userRepository.findByPhone(phone).orElse(null);
        boolean credentialsValid = user != null
                && user.getStatus() == UserStatus.ACTIVE
                && user.getPasswordHash() != null
                && passwordEncoder.matches(requestDto.getPassword(), user.getPasswordHash());

        if (!credentialsValid) {
            registerFailedAttempt(phone);
            securityAuditService.record(user, phone, SecurityEventType.LOGIN_FAILURE, clientIp(), userAgent(), null);
            throw new AuthenticationFailedException(INVALID_CREDENTIALS_MESSAGE);
        }

        cacheService.resetLoginAttempts(phone);
        securityAuditService.record(user, phone, SecurityEventType.LOGIN_SUCCESS, clientIp(), userAgent(), null);
        return completeAuthentication(user);
    }

    @Override
    public AuthTokenResponseDto loginWithGoogle(SocialLoginRequestDto requestDto) {
        GoogleUserInfo googleUserInfo = googleTokenVerifierService.verify(requestDto.getIdToken());

        // Committed in its own REQUIRES_NEW transaction: securityAuditService.record()
        // below also runs REQUIRES_NEW, and a suspended-but-uncommitted outer
        // transaction is invisible to it — a first-time Google sign-in would
        // otherwise fail the audit-log insert's user_id foreign key.
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        User user = requiresNew.execute(status -> {
            User resolved = userRepository.findByGoogleSub(googleUserInfo.subject())
                    .or(() -> userRepository.findByEmail(googleUserInfo.email())
                            .map(existing -> {
                                existing.setGoogleSub(googleUserInfo.subject());
                                return existing;
                            }))
                    .orElseGet(() -> {
                        User created = new User();
                        created.setEmail(googleUserInfo.email());
                        created.setFullName(googleUserInfo.fullName());
                        created.setAvatarUrl(googleUserInfo.pictureUrl());
                        created.setGoogleSub(googleUserInfo.subject());
                        created.setStatus(UserStatus.ACTIVE);
                        return created;
                    });
            return userRepository.save(resolved);
        });

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthenticationFailedException("Account is not active");
        }

        securityAuditService.record(user, user.getPhone(), SecurityEventType.SOCIAL_LOGIN_GOOGLE, clientIp(), userAgent(), null);
        return completeAuthentication(user);
    }

    @Override
    public AuthTokenResponseDto verifyMfa(MfaVerifyRequestDto requestDto) {
        Long userId = cacheService.getMfaChallengeUserId(requestDto.getMfaToken())
                .orElseThrow(() -> new AuthenticationFailedException(INVALID_MFA_CHALLENGE_MESSAGE));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationFailedException(INVALID_MFA_CHALLENGE_MESSAGE));

        if (!otpService.verifyOtp(user.getPhone(), OtpPurpose.LOGIN_MFA, requestDto.getCode())) {
            securityAuditService.record(user, user.getPhone(), SecurityEventType.MFA_FAILURE, clientIp(), userAgent(), null);
            throw new AuthenticationFailedException(INVALID_MFA_CODE_MESSAGE);
        }

        cacheService.deleteMfaChallenge(requestDto.getMfaToken());
        securityAuditService.record(user, user.getPhone(), SecurityEventType.MFA_SUCCESS, clientIp(), userAgent(), null);
        return issueTokenResponse(user, null);
    }

    @Override
    public void enableMfa(Long userId, String password) {
        User user = requireUserWithPassword(userId, password);
        user.setMfaEnabled(true);
        userRepository.save(user);
        securityAuditService.record(user, user.getPhone(), SecurityEventType.MFA_ENABLED, clientIp(), userAgent(), null);
    }

    @Override
    public void disableMfa(Long userId, String password) {
        User user = requireUserWithPassword(userId, password);
        user.setMfaEnabled(false);
        userRepository.save(user);
        securityAuditService.record(user, user.getPhone(), SecurityEventType.MFA_DISABLED, clientIp(), userAgent(), null);
    }

    @Override
    public AuthTokenResponseDto refreshToken(RefreshTokenRequestDto requestDto) {
        RefreshTokenId refreshTokenId = parseRefreshToken(requestDto.getRefreshToken());

        if (!cacheService.existsRefreshToken(refreshTokenId.userId(), refreshTokenId.tokenId())) {
            throw new AuthenticationFailedException(INVALID_REFRESH_TOKEN_MESSAGE);
        }
        cacheService.deleteRefreshToken(refreshTokenId.userId(), refreshTokenId.tokenId());

        User user = userRepository.findById(refreshTokenId.userId())
                .orElseThrow(() -> new AuthenticationFailedException(INVALID_REFRESH_TOKEN_MESSAGE));

        UserSession session = userSessionRepository
                .findByUser_IdAndRefreshTokenId(user.getId(), refreshTokenId.tokenId())
                .orElse(null);

        securityAuditService.record(user, user.getPhone(), SecurityEventType.TOKEN_REFRESH, clientIp(), userAgent(), null);
        return issueTokenResponse(user, session);
    }

    @Override
    public void logout(LogoutRequestDto requestDto) {
        RefreshTokenId refreshTokenId = parseRefreshToken(requestDto.getRefreshToken());
        cacheService.deleteRefreshToken(refreshTokenId.userId(), refreshTokenId.tokenId());
        userSessionRepository.findByUser_IdAndRefreshTokenId(refreshTokenId.userId(), refreshTokenId.tokenId())
                .ifPresent(session -> {
                    session.setRevoked(true);
                    session.setRevokedAt(Instant.now());
                    userSessionRepository.save(session);
                });

        User user = userRepository.findById(refreshTokenId.userId()).orElse(null);
        securityAuditService.record(user, user != null ? user.getPhone() : null,
                SecurityEventType.LOGOUT, clientIp(), userAgent(), null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionResponseDto> listSessions(Long userId) {
        return userSessionRepository.findByUser_IdAndRevokedFalseOrderByLastUsedAtDesc(userId).stream()
                .map(session -> SessionResponseDto.builder()
                        .id(session.getId())
                        .deviceLabel(session.getDeviceLabel())
                        .ipAddress(session.getIpAddress())
                        .userAgent(session.getUserAgent())
                        .createdAt(session.getCreatedAt())
                        .lastUsedAt(session.getLastUsedAt())
                        .build())
                .toList();
    }

    @Override
    public void revokeSession(Long userId, Long sessionId) {
        UserSession session = userSessionRepository.findById(sessionId)
                .filter(s -> s.getUser().getId().equals(userId))
                .orElseThrow(() -> new EntityNotFoundException("Session not found"));

        session.setRevoked(true);
        session.setRevokedAt(Instant.now());
        userSessionRepository.save(session);
        cacheService.deleteRefreshToken(userId, session.getRefreshTokenId());
        securityAuditService.record(session.getUser(), session.getUser().getPhone(),
                SecurityEventType.SESSION_REVOKED, clientIp(), userAgent(), "sessionId=" + sessionId);
    }

    @Override
    public void revokeAllSessions(Long userId) {
        revokeAllSessionRows(userId);
        cacheService.deleteAllRefreshTokens(userId);
        User user = userRepository.findById(userId).orElse(null);
        securityAuditService.record(user, user != null ? user.getPhone() : null,
                SecurityEventType.ALL_SESSIONS_REVOKED, clientIp(), userAgent(), null);
    }

    @Override
    public void forgotPassword(ForgotPasswordRequestDto requestDto) {
        String phone = requestDto.getPhone();
        if (userRepository.findByPhone(phone).isPresent()) {
            otpService.sendOtp(phone, OtpPurpose.PASSWORD_RESET);
        }
        // ponytail: same response whether or not the phone is registered, so
        // this endpoint can't be used to enumerate registered phone numbers.
        securityAuditService.record(null, phone, SecurityEventType.PASSWORD_RESET_REQUESTED, clientIp(), userAgent(), null);
    }

    @Override
    public void resetPassword(ResetPasswordRequestDto requestDto) {
        User user = userRepository.findByPhone(requestDto.getPhone())
                .orElseThrow(() -> new AuthenticationFailedException(INVALID_OTP_MESSAGE));

        if (!otpService.verifyOtp(user.getPhone(), OtpPurpose.PASSWORD_RESET, requestDto.getCode())) {
            throw new AuthenticationFailedException(INVALID_OTP_MESSAGE);
        }

        user.setPasswordHash(passwordEncoder.encode(requestDto.getNewPassword()));
        userRepository.save(user);
        revokeAllSessionRows(user.getId());
        cacheService.deleteAllRefreshTokens(user.getId());
        securityAuditService.record(user, user.getPhone(), SecurityEventType.PASSWORD_RESET_COMPLETED, clientIp(), userAgent(), null);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequestDto requestDto) {
        User user = requireUserWithPassword(userId, requestDto.getOldPassword());
        user.setPasswordHash(passwordEncoder.encode(requestDto.getNewPassword()));
        userRepository.save(user);
        revokeAllSessionRows(user.getId());
        cacheService.deleteAllRefreshTokens(user.getId());
        securityAuditService.record(user, user.getPhone(), SecurityEventType.PASSWORD_CHANGED, clientIp(), userAgent(), null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SecurityAuditLogResponseDto> listSecurityLog(Long userId) {
        return securityAuditService.recentForUser(userId).stream()
                .map(log -> SecurityAuditLogResponseDto.builder()
                        .id(log.getId())
                        .eventType(log.getEventType().name())
                        .ipAddress(log.getIpAddress())
                        .userAgent(log.getUserAgent())
                        .detail(log.getDetail())
                        .createdAt(log.getCreatedAt())
                        .build())
                .toList();
    }

    private void registerFailedAttempt(String phone) {
        long attempts = cacheService.incrementLoginAttempts(phone, authProperties.loginAttemptsWindow());
        if (attempts >= authProperties.maxLoginAttempts()) {
            cacheService.lockLogin(phone, authProperties.loginLockTtl());
            securityAuditService.record(null, phone, SecurityEventType.ACCOUNT_LOCKED, clientIp(), userAgent(), null);
        }
    }

    private AuthTokenResponseDto completeAuthentication(User user) {
        if (user.isMfaEnabled()) {
            return issueMfaChallenge(user);
        }
        return issueTokenResponse(user, null);
    }

    private AuthTokenResponseDto issueMfaChallenge(User user) {
        otpService.sendOtp(user.getPhone(), OtpPurpose.LOGIN_MFA);
        String challengeToken = UUID.randomUUID().toString();
        cacheService.storeMfaChallenge(challengeToken, user.getId(), authProperties.mfaChallengeTtl());
        securityAuditService.record(user, user.getPhone(), SecurityEventType.MFA_CHALLENGE_ISSUED, clientIp(), userAgent(), null);
        return AuthTokenResponseDto.builder()
                .mfaRequired(true)
                .mfaToken(challengeToken)
                .expiresIn(authProperties.mfaChallengeTtl().toSeconds())
                .build();
    }

    // existingSession != null on refresh (rotates the same session row so the
    // "active sessions" list doesn't grow a new row every access-token TTL).
    private AuthTokenResponseDto issueTokenResponse(User user, UserSession existingSession) {
        Long authorProfileId = authorProfileRepository.findByUser_Id(user.getId())
                .map(AuthorProfile::getId)
                .orElse(null);
        List<String> roles = authorProfileId != null ? List.of("READER", "AUTHOR") : List.of("READER");

        Jwt accessToken = issueAccessToken(user, roles, authorProfileId);

        String tokenId = UUID.randomUUID().toString();
        cacheService.storeRefreshToken(user.getId(), tokenId, authProperties.refreshTokenTtl());
        recordSession(user, tokenId, existingSession);

        return AuthTokenResponseDto.builder()
                .accessToken(accessToken.getTokenValue())
                .refreshToken(user.getId() + ":" + tokenId)
                .expiresIn(authProperties.accessTokenTtl().toSeconds())
                .tokenType("Bearer")
                .userId(user.getId())
                .fullName(user.getFullName())
                .roles(roles)
                .authorProfileId(authorProfileId)
                .build();
    }

    private void recordSession(User user, String tokenId, UserSession existingSession) {
        UserSession session = existingSession != null ? existingSession : new UserSession();
        session.setUser(user);
        session.setRefreshTokenId(tokenId);
        session.setIpAddress(clientIp());
        session.setUserAgent(userAgent());
        if (existingSession == null) {
            session.setDeviceLabel(userAgent());
        }
        session.setLastUsedAt(Instant.now());
        session.setRevoked(false);
        userSessionRepository.save(session);
    }

    private void revokeAllSessionRows(Long userId) {
        List<UserSession> sessions = userSessionRepository.findByUser_IdAndRevokedFalse(userId);
        Instant now = Instant.now();
        sessions.forEach(session -> {
            session.setRevoked(true);
            session.setRevokedAt(now);
        });
        userSessionRepository.saveAll(sessions);
    }

    // ponytail: social-only accounts (no password set) can't confirm sensitive
    // actions via this path yet. Upgrade: accept a fresh Google ID token as the
    // confirmation factor for those accounts.
    private User requireUserWithPassword(Long userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationFailedException(INVALID_CREDENTIALS_MESSAGE));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthenticationFailedException(INVALID_CREDENTIALS_MESSAGE);
        }
        return user;
    }

    private Jwt issueAccessToken(User user, List<String> roles, Long authorProfileId) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(authProperties.accessTokenTtl()))
                .claim("roles", roles);
        if (authorProfileId != null) {
            claims.claim("authorProfileId", authorProfileId);
        }

        JwsHeader jwsHeader = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims.build()));
    }

    // ponytail: refresh token is "{userId}:{tokenId}" — userId is a public
    // selector for the Redis key, tokenId is the unguessable secret. Avoids a
    // second lookup-by-value step while keeping the secret part fully random.
    private RefreshTokenId parseRefreshToken(String rawToken) {
        int separatorIndex = rawToken.indexOf(':');
        if (separatorIndex < 0) {
            throw new AuthenticationFailedException(INVALID_REFRESH_TOKEN_MESSAGE);
        }
        try {
            Long userId = Long.parseLong(rawToken.substring(0, separatorIndex));
            String tokenId = rawToken.substring(separatorIndex + 1);
            return new RefreshTokenId(userId, tokenId);
        } catch (NumberFormatException e) {
            throw new AuthenticationFailedException(INVALID_REFRESH_TOKEN_MESSAGE);
        }
    }

    // ponytail: getRemoteAddr() is the direct TCP peer — behind a reverse
    // proxy/load balancer this is the proxy, not the client. Upgrade to reading
    // X-Forwarded-For once deployed behind one.
    private String clientIp() {
        return httpServletRequest.getRemoteAddr();
    }

    private String userAgent() {
        return httpServletRequest.getHeader("User-Agent");
    }

    private record RefreshTokenId(Long userId, String tokenId) {}
}
