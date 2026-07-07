package com.prishtha.mvp.identity;

import com.prishtha.mvp.identity.api.contract.IdentityService;
import com.prishtha.mvp.identity.api.dto.request.UserSignUpRequestDto;
import com.prishtha.mvp.identity.api.dto.response.UserBasicInfoResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class IdentityServiceIntegrationTests {

    @Autowired
    private IdentityService identityService;

    @Test
    void testSignUpAndVerifyOtpFlow() {
        UserSignUpRequestDto requestDto = UserSignUpRequestDto.builder()
                .phone("01712345678")
                .fullName("Test User")
                .password("password123")
                .build();

        UserBasicInfoResponseDto signUpResponse = identityService.signUp(requestDto);
        assertThat(signUpResponse.getId()).isNotNull();
        assertThat(signUpResponse.getPhone()).isEqualTo("01712345678");
        assertThat(signUpResponse.getFullName()).isEqualTo("Test User");
        assertThat(signUpResponse.getStatus()).isEqualTo("PENDING_VERIFICATION");

        // Verify with invalid OTP should fail
        assertThrows(IllegalArgumentException.class, () ->
                identityService.verifyOtp("01712345678", "000000")
        );

        // Verify with correct OTP
        UserBasicInfoResponseDto verifyResponse = identityService.verifyOtp("01712345678", "123456");
        assertThat(verifyResponse.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void testSignUpDuplicatePhoneShouldThrowException() {
        UserSignUpRequestDto requestDto = UserSignUpRequestDto.builder()
                .phone("01987654321")
                .fullName("Test User 2")
                .password("password123")
                .build();

        identityService.signUp(requestDto);

        assertThrows(IllegalArgumentException.class, () ->
                identityService.signUp(requestDto)
        );
    }
}
