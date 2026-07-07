package com.prishtha.mvp.identity.api.controller;

import com.prishtha.mvp.identity.api.contract.IdentityService;
import com.prishtha.mvp.identity.api.dto.request.UserSignUpRequestDto;
import com.prishtha.mvp.identity.api.dto.response.UserBasicInfoResponseDto;
import com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(IdentityRouteConstant.AUTH_BASE_PATH)
@RequiredArgsConstructor
public class IdentityController {

    private final IdentityService identityService;

    @PostMapping(IdentityRouteConstant.SIGN_UP)
    public ResponseEntity<UserBasicInfoResponseDto> signUp(@Valid @RequestBody UserSignUpRequestDto requestDto) {
        UserBasicInfoResponseDto response = identityService.signUp(requestDto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping(IdentityRouteConstant.VERIFY_OTP)
    public ResponseEntity<UserBasicInfoResponseDto> verifyOtp(
            @RequestParam String phone,
            @RequestParam String code) {
        UserBasicInfoResponseDto response = identityService.verifyOtp(phone, code);
        return ResponseEntity.ok(response);
    }
}
