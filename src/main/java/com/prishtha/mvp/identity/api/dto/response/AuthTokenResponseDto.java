package com.prishtha.mvp.identity.api.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthTokenResponseDto {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private String tokenType;
    private Long userId;
    private String fullName;
    private List<String> roles;
    private Long authorProfileId;

    // Set instead of the token fields above when the account has MFA enabled;
    // the client must call POST /mfa/verify with this token to complete login.
    private boolean mfaRequired;
    private String mfaToken;
}
