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
import com.prishtha.mvp.shared.exception.AuthenticationFailedException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// Package matches internal.service (not api) so tests can reach the
// package-private OtpDeliveryService/GoogleTokenVerifierService collaborators
// to capture/stub what would otherwise leave the module (an SMS/Google call).
@SpringBootTest
@Import(AuthServiceIntegrationTests.CapturingOtpDeliveryConfig.class)
@Transactional
class AuthServiceIntegrationTests {

    @Autowired
    private IdentityService identityService;

    @Autowired
    private AuthService authService;

    @Autowired
    private OtpDeliveryService otpDeliveryService;

    @MockitoBean
    private GoogleTokenVerifierService googleTokenVerifierService;

    private String signUpAndActivate(String phone) {
        identityService.signUp(UserSignUpRequestDto.builder()
                .phone(phone)
                .fullName("Test User")
                .password("password123")
                .build());
        identityService.verifyOtp(phone, lastOtpFor(phone));
        return phone;
    }

    private String lastOtpFor(String phone) {
        return ((CapturingOtpDeliveryService) otpDeliveryService).lastCode(phone);
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

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRoles()).containsExactly("READER");
    }

    @TestConfiguration
    static class CapturingOtpDeliveryConfig {
        @Bean
        @Primary
        OtpDeliveryService capturingOtpDeliveryService() {
            return new CapturingOtpDeliveryService();
        }
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
