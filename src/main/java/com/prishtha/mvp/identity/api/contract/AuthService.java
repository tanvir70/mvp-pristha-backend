package com.prishtha.mvp.identity.api.contract;

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
import java.util.List;

public interface AuthService {
    AuthTokenResponseDto login(LoginRequestDto requestDto);
    AuthTokenResponseDto refreshToken(RefreshTokenRequestDto requestDto);
    void logout(LogoutRequestDto requestDto);

    AuthTokenResponseDto loginWithGoogle(SocialLoginRequestDto requestDto);
    AuthTokenResponseDto verifyMfa(MfaVerifyRequestDto requestDto);
    void enableMfa(Long userId, String password);
    void disableMfa(Long userId, String password);

    List<SessionResponseDto> listSessions(Long userId);
    void revokeSession(Long userId, Long sessionId);
    void revokeAllSessions(Long userId);

    void forgotPassword(ForgotPasswordRequestDto requestDto);
    void resetPassword(ResetPasswordRequestDto requestDto);
    void changePassword(Long userId, ChangePasswordRequestDto requestDto);

    List<SecurityAuditLogResponseDto> listSecurityLog(Long userId);
}
