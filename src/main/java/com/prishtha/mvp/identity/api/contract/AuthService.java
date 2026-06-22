package com.prishtha.mvp.identity.api.contract;

import com.prishtha.mvp.identity.api.dto.request.LoginRequestDto;
import com.prishtha.mvp.identity.api.dto.request.LogoutRequestDto;
import com.prishtha.mvp.identity.api.dto.request.RefreshTokenRequestDto;
import com.prishtha.mvp.identity.api.dto.response.AuthTokenResponseDto;

public interface AuthService {
    AuthTokenResponseDto login(LoginRequestDto requestDto);
    AuthTokenResponseDto refreshToken(RefreshTokenRequestDto requestDto);
    void logout(LogoutRequestDto requestDto);
}
