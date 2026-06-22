package com.prishtha.mvp.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prishtha.mvp.identity.api.contract.AuthService;
import com.prishtha.mvp.identity.api.contract.IdentityService;
import com.prishtha.mvp.identity.api.dto.request.LoginRequestDto;
import com.prishtha.mvp.identity.api.dto.request.LogoutRequestDto;
import com.prishtha.mvp.identity.api.dto.request.RefreshTokenRequestDto;
import com.prishtha.mvp.identity.api.dto.request.UserSignUpRequestDto;
import com.prishtha.mvp.identity.api.dto.response.AuthTokenResponseDto;
import com.prishtha.mvp.shared.exception.AuthenticationFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AuthServiceIntegrationTests {

    @Autowired
    private IdentityService identityService;

    @Autowired
    private AuthService authService;

    private String signUpAndActivate(String phone) {
        identityService.signUp(UserSignUpRequestDto.builder()
                .phone(phone)
                .fullName("Test User")
                .password("password123")
                .build());
        identityService.verifyOtp(phone, "123456");
        return phone;
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
}
