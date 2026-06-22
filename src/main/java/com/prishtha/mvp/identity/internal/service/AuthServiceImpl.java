package com.prishtha.mvp.identity.internal.service;

import com.prishtha.mvp.identity.api.contract.AuthService;
import com.prishtha.mvp.identity.api.dto.request.LoginRequestDto;
import com.prishtha.mvp.identity.api.dto.request.LogoutRequestDto;
import com.prishtha.mvp.identity.api.dto.request.RefreshTokenRequestDto;
import com.prishtha.mvp.identity.api.dto.response.AuthTokenResponseDto;
import com.prishtha.mvp.identity.internal.config.AuthProperties;
import com.prishtha.mvp.identity.internal.entity.AuthorProfile;
import com.prishtha.mvp.identity.internal.entity.User;
import com.prishtha.mvp.identity.internal.enums.UserStatus;
import com.prishtha.mvp.identity.internal.repository.AuthorProfileRepository;
import com.prishtha.mvp.identity.internal.repository.UserRepository;
import com.prishtha.mvp.shared.exception.AuthenticationFailedException;
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
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
class AuthServiceImpl implements AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid phone number or password";
    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid or expired refresh token";

    private final UserRepository userRepository;
    private final AuthorProfileRepository authorProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final CacheService cacheService;
    private final AuthProperties authProperties;

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
                && passwordEncoder.matches(requestDto.getPassword(), user.getPasswordHash());

        if (!credentialsValid) {
            registerFailedAttempt(phone);
            throw new AuthenticationFailedException(INVALID_CREDENTIALS_MESSAGE);
        }

        cacheService.resetLoginAttempts(phone);
        return issueTokenResponse(user);
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

        return issueTokenResponse(user);
    }

    @Override
    public void logout(LogoutRequestDto requestDto) {
        RefreshTokenId refreshTokenId = parseRefreshToken(requestDto.getRefreshToken());
        cacheService.deleteRefreshToken(refreshTokenId.userId(), refreshTokenId.tokenId());
    }

    private void registerFailedAttempt(String phone) {
        long attempts = cacheService.incrementLoginAttempts(phone, authProperties.loginAttemptsWindow());
        if (attempts >= authProperties.maxLoginAttempts()) {
            cacheService.lockLogin(phone, authProperties.loginLockTtl());
        }
    }

    private AuthTokenResponseDto issueTokenResponse(User user) {
        Long authorProfileId = authorProfileRepository.findByUser_Id(user.getId())
                .map(AuthorProfile::getId)
                .orElse(null);
        List<String> roles = authorProfileId != null ? List.of("READER", "AUTHOR") : List.of("READER");

        Jwt accessToken = issueAccessToken(user, roles, authorProfileId);

        String tokenId = UUID.randomUUID().toString();
        cacheService.storeRefreshToken(user.getId(), tokenId, authProperties.refreshTokenTtl());

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

    private record RefreshTokenId(Long userId, String tokenId) {}
}
