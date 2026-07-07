package com.prishtha.mvp.identity.api.controller;

import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.AUTH_BASE_PATH;
import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.MFA_DISABLE;
import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.MFA_ENABLE;
import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.PASSWORD_CHANGE;
import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.PASSWORD_FORGOT;
import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.PASSWORD_RESET;
import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.SECURITY_LOG;
import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.SESSIONS;

import com.prishtha.mvp.identity.api.contract.AuthService;
import com.prishtha.mvp.identity.api.dto.request.ChangePasswordRequestDto;
import com.prishtha.mvp.identity.api.dto.request.ForgotPasswordRequestDto;
import com.prishtha.mvp.identity.api.dto.request.MfaToggleRequestDto;
import com.prishtha.mvp.identity.api.dto.request.ResetPasswordRequestDto;
import com.prishtha.mvp.identity.api.dto.response.SecurityAuditLogResponseDto;
import com.prishtha.mvp.identity.api.dto.response.SessionResponseDto;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AUTH_BASE_PATH)
@RequiredArgsConstructor
public class AccountSecurityController {

    private final AuthService authService;

    @PostMapping(MFA_ENABLE)
    public ResponseEntity<Void> enableMfa(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody MfaToggleRequestDto requestDto) {
        authService.enableMfa(Long.valueOf(jwt.getSubject()), requestDto.getPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(MFA_DISABLE)
    public ResponseEntity<Void> disableMfa(@AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody MfaToggleRequestDto requestDto) {
        authService.disableMfa(Long.valueOf(jwt.getSubject()), requestDto.getPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(PASSWORD_FORGOT)
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto requestDto) {
        authService.forgotPassword(requestDto);
        return ResponseEntity.accepted().build();
    }

    @PostMapping(PASSWORD_RESET)
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequestDto requestDto) {
        authService.resetPassword(requestDto);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(PASSWORD_CHANGE)
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody ChangePasswordRequestDto requestDto) {
        authService.changePassword(Long.valueOf(jwt.getSubject()), requestDto);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(SESSIONS)
    public ResponseEntity<List<SessionResponseDto>> listSessions(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(authService.listSessions(Long.valueOf(jwt.getSubject())));
    }

    @DeleteMapping(SESSIONS + "/{sessionId}")
    public ResponseEntity<Void> revokeSession(@AuthenticationPrincipal Jwt jwt, @PathVariable Long sessionId) {
        authService.revokeSession(Long.valueOf(jwt.getSubject()), sessionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping(SESSIONS)
    public ResponseEntity<Void> revokeAllSessions(@AuthenticationPrincipal Jwt jwt) {
        authService.revokeAllSessions(Long.valueOf(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping(SECURITY_LOG)
    public ResponseEntity<List<SecurityAuditLogResponseDto>> listSecurityLog(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(authService.listSecurityLog(Long.valueOf(jwt.getSubject())));
    }
}
