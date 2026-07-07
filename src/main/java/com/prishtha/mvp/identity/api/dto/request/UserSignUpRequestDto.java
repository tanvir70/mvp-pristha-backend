package com.prishtha.mvp.identity.api.dto.request;

import static com.prishtha.mvp.identity.internal.util.constant.AuthValidationConstant.BD_PHONE_MESSAGE;
import static com.prishtha.mvp.identity.internal.util.constant.AuthValidationConstant.BD_PHONE_PATTERN;
import static com.prishtha.mvp.identity.internal.util.constant.AuthValidationConstant.PASSWORD_MESSAGE;
import static com.prishtha.mvp.identity.internal.util.constant.AuthValidationConstant.PASSWORD_PATTERN;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSignUpRequestDto {
    @NotBlank
    @Pattern(regexp = BD_PHONE_PATTERN, message = BD_PHONE_MESSAGE)
    private String phone;

    @NotBlank
    @Size(max = 100)
    private String fullName;

    @NotBlank
    @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
    private String password;

    @NotBlank
    private String confirmPassword;

    @AssertTrue(message = "Password and confirm password must match")
    private boolean isPasswordConfirmed() {
        return password == null || password.equals(confirmPassword);
    }
}
