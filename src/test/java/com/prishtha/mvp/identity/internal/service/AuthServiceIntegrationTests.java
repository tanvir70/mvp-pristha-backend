package com.prishtha.mvp.identity.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.prishtha.mvp.identity.api.contract.AuthService;
import com.prishtha.mvp.identity.api.contract.IdentityService;
import com.prishtha.mvp.identity.api.dto.request.ChangePasswordRequestDto;
import com.prishtha.mvp.identity.api.dto.request.ForgotPasswordRequestDto;
import com.prishtha.mvp.identity.api.dto.request.LoginRequestDto;
import com.prishtha.mvp.identity.api.dto.request.LogoutRequestDto;
import com.prishtha.mvp.identity.api.dto.request.MfaVerifyRequestDto;
import com.prishtha.mvp.identity.api.dto.request.RefreshTokenRequestDto;
import com.prishtha.mvp.identity.api.dto.request.ResetPasswordRequestDto;
import com.prishtha.mvp.identity.api.dto.request.SocialLoginRequestDto;
import com.prishtha.mvp.identity.api.dto.request.UserSignUpRequestDto;
import com.prishtha.mvp.identity.api.dto.response.AuthTokenResponseDto;
import com.prishtha.mvp.identity.api.dto.response.SessionResponseDto;
import com.prishtha.mvp.identity.internal.repository.SecurityAuditLogRepository;
import com.prishtha.mvp.identity.internal.repository.UserRepository;
import com.prishtha.mvp.identity.internal.repository.UserSessionRepository;
import com.prishtha.mvp.shared.exception.AuthenticationFailedException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// No class-level @Transactional: AuthServiceImpl writes security-audit-log
// rows in a REQUIRES_NEW transaction, which can't see rows created by an
// outer transaction that a test-rollback would otherwise never commit.
// Created users are cleaned up explicitly in tearDown() instead.
@SpringBootTest
@Import(CapturingOtpDeliveryTestSupport.class)
class AuthServiceIntegrationTests {

    @Autowired
    private IdentityService identityService;

    @Autowired
    private AuthService authService;

    @Autowired
    private OtpDeliveryService otpDeliveryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private SecurityAuditLogRepository securityAuditLogRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private GoogleTokenVerifierService googleTokenVerifierService;

    private final List<Long> createdUserIds = new ArrayList<>();

    // Plain @Transactional on an @AfterEach method isn't wrapped by Spring's
    // test transaction support (that only applies to @Test methods), so this
    // runs the cleanup in its own, genuinely committing transaction.
    @AfterEach
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (Long userId : createdUserIds) {
                securityAuditLogRepository.deleteByUser_Id(userId);
                userSessionRepository.deleteByUser_Id(userId);
                userRepository.deleteById(userId);
            }
        });
    }

    private String signUpAndActivate(String phone) {
        Long userId = identityService.signUp(UserSignUpRequestDto.builder()
                .phone(phone)
                .fullName("Test User")
                .password("password123")
                .build()).getId();
        createdUserIds.add(userId);
        identityService.verifyOtp(phone, lastOtpFor(phone));
        return phone;
    }

    private String lastOtpFor(String phone) {
        return ((CapturingOtpDeliveryTestSupport.CapturingOtpDeliveryService) otpDeliveryService).lastCode(phone);
    }

    @Test
    void loginSuccessIssuesVerifiableJwtAndRefreshToken() {
        String phone = signUpAndActivate("01711111111");

        AuthTokenResponseDto response = authService.login(
                LoginRequestDto.builder().phone(phone).password("password123").build());

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getAccessToken().split("\\.")).hasSize(3);
        assertThat(response.getRefreshToken()).contains(":");
        assertThat(response.getRoles()).containsExactly("READER");
        assertThat(response.getExpiresIn()).isEqualTo(900);
    }

    @Test
    void loginWithWrongPasswordFailsWith401() {
        String phone = signUpAndActivate("01722222222");

        assertThrows(AuthenticationFailedException.class, () -> authService.login(
                LoginRequestDto.builder().phone(phone).password("wrong-password").build()));
    }

    @Test
    void fiveConsecutiveFailuresLocksTheAccount() {
        String phone = signUpAndActivate("01733333333");

        for (int i = 0; i < 5; i++) {
            assertThrows(AuthenticationFailedException.class, () -> authService.login(
                    LoginRequestDto.builder().phone(phone).password("wrong-password").build()));
        }

        // Even the correct password is now rejected — account is locked.
        assertThrows(AuthenticationFailedException.class, () -> authService.login(
                LoginRequestDto.builder().phone(phone).password("password123").build()));
    }

    @Test
    void refreshRotatesTokenAndInvalidatesThePreviousOne() {
        String phone = signUpAndActivate("01744444444");
        AuthTokenResponseDto loginResponse = authService.login(
                LoginRequestDto.builder().phone(phone).password("password123").build());

        AuthTokenResponseDto refreshResponse = authService.refreshToken(
                RefreshTokenRequestDto.builder().refreshToken(loginResponse.getRefreshToken()).build());

        assertThat(refreshResponse.getRefreshToken()).isNotEqualTo(loginResponse.getRefreshToken());
        assertThrows(AuthenticationFailedException.class, () -> authService.refreshToken(
                RefreshTokenRequestDto.builder().refreshToken(loginResponse.getRefreshToken()).build()));
    }

    @Test
    void logoutInvalidatesTheRefreshToken() {
        String phone = signUpAndActivate("01755555555");
        AuthTokenResponseDto loginResponse = authService.login(
                LoginRequestDto.builder().phone(phone).password("password123").build());

        authService.logout(LogoutRequestDto.builder().refreshToken(loginResponse.getRefreshToken()).build());

        assertThrows(AuthenticationFailedException.class, () -> authService.refreshToken(
                RefreshTokenRequestDto.builder().refreshToken(loginResponse.getRefreshToken()).build()));
    }

    @Test
    void loginWithMfaEnabledIssuesChallengeThenVerifyCompletesLogin() {
        String phone = signUpAndActivate("01766666666");
        AuthTokenResponseDto initialLogin = authService.login(
                LoginRequestDto.builder().phone(phone).password("password123").build());
        authService.enableMfa(initialLogin.getUserId(), "password123");

        AuthTokenResponseDto challenge = authService.login(
                LoginRequestDto.builder().phone(phone).password("password123").build());
        assertThat(challenge.isMfaRequired()).isTrue();
        assertThat(challenge.getAccessToken()).isNull();

        AuthTokenResponseDto completed = authService.verifyMfa(
                MfaVerifyRequestDto.builder().mfaToken(challenge.getMfaToken()).code(lastOtpFor(phone)).build());
        assertThat(completed.getAccessToken()).isNotBlank();
        assertThat(completed.isMfaRequired()).isFalse();
    }

    @Test
    void sessionCanBeListedAndRevoked() {
        String phone = signUpAndActivate("01777777777");
        AuthTokenResponseDto loginResponse = authService.login(
                LoginRequestDto.builder().phone(phone).password("password123").build());

        List<SessionResponseDto> sessions = authService.listSessions(loginResponse.getUserId());
        assertThat(sessions).hasSize(1);

        authService.revokeSession(loginResponse.getUserId(), sessions.get(0).getId());
        assertThat(authService.listSessions(loginResponse.getUserId())).isEmpty();
        assertThrows(AuthenticationFailedException.class, () -> authService.refreshToken(
                RefreshTokenRequestDto.builder().refreshToken(loginResponse.getRefreshToken()).build()));
    }

    @Test
    void changePasswordInvalidatesExistingRefreshToken() {
        String phone = signUpAndActivate("01788888888");
        AuthTokenResponseDto loginResponse = authService.login(
                LoginRequestDto.builder().phone(phone).password("password123").build());

        authService.changePassword(loginResponse.getUserId(), ChangePasswordRequestDto.builder()
                .oldPassword("password123").newPassword("newPassword456").build());

        assertThrows(AuthenticationFailedException.class, () -> authService.refreshToken(
                RefreshTokenRequestDto.builder().refreshToken(loginResponse.getRefreshToken()).build()));
        assertThat(authService.login(LoginRequestDto.builder().phone(phone).password("newPassword456").build())
                .getAccessToken()).isNotBlank();
    }

    @Test
    void forgotPasswordThenResetAllowsLoginWithNewPassword() {
        String phone = signUpAndActivate("01799999999");

        authService.forgotPassword(ForgotPasswordRequestDto.builder().phone(phone).build());
        authService.resetPassword(ResetPasswordRequestDto.builder()
                .phone(phone).code(lastOtpFor(phone)).newPassword("resetPassword789").build());

        assertThrows(AuthenticationFailedException.class, () -> authService.login(
                LoginRequestDto.builder().phone(phone).password("password123").build()));
        assertThat(authService.login(LoginRequestDto.builder().phone(phone).password("resetPassword789").build())
                .getAccessToken()).isNotBlank();
    }

    @Test
    void googleLoginCreatesAccountAndIssuesTokens() {
        when(googleTokenVerifierService.verify("valid-google-token"))
                .thenReturn(new GoogleUserInfo("google-sub-1", "new.user@example.com", "Google User", null));

        AuthTokenResponseDto response = authService.loginWithGoogle(
                SocialLoginRequestDto.builder().idToken("valid-google-token").build());
        createdUserIds.add(response.getUserId());

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRoles()).containsExactly("READER");
    }
}
