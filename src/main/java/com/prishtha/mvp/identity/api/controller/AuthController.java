package com.prishtha.mvp.identity.api.controller;

import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.AUTH_BASE_PATH;
import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.LOGIN;
import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.LOGOUT;
import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.REFRESH;

import com.prishtha.mvp.identity.api.contract.AuthService;
import com.prishtha.mvp.identity.api.dto.request.LoginRequestDto;
import com.prishtha.mvp.identity.api.dto.request.LogoutRequestDto;
import com.prishtha.mvp.identity.api.dto.request.RefreshTokenRequestDto;
import com.prishtha.mvp.identity.api.dto.response.AuthTokenResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AUTH_BASE_PATH)
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping(LOGIN)
    public ResponseEntity<AuthTokenResponseDto> login(@Valid @RequestBody LoginRequestDto requestDto) {
        return ResponseEntity.ok(authService.login(requestDto));
    }

    @PostMapping(REFRESH)
    public ResponseEntity<AuthTokenResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto requestDto) {
        return ResponseEntity.ok(authService.refreshToken(requestDto));
    }

    @PostMapping(LOGOUT)
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequestDto requestDto) {
        authService.logout(requestDto);
        return ResponseEntity.noContent().build();
    }
}
