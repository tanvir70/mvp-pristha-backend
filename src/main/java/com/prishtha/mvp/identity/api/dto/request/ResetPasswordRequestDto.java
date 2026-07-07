package com.prishtha.mvp.identity.api.dto.request;

import static com.prishtha.mvp.identity.internal.util.constant.AuthValidationConstant.PASSWORD_MESSAGE;
import static com.prishtha.mvp.identity.internal.util.constant.AuthValidationConstant.PASSWORD_PATTERN;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequestDto {
    @NotBlank
    private String phone;

    @NotBlank
    private String code;

    @NotBlank
    @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
    private String newPassword;
}
