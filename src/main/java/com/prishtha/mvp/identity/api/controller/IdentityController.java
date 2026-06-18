package com.prishtha.mvp.identity.api.controller;

import com.prishtha.mvp.identity.api.contract.IdentityService;
import com.prishtha.mvp.identity.api.dto.request.UserSignUpRequestDto;
import com.prishtha.mvp.identity.api.dto.response.UserBasicInfoResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class IdentityController {

    private final IdentityService identityService;

    @PostMapping("/signup")
    public ResponseEntity<UserBasicInfoResponseDto> signUp(@RequestBody UserSignUpRequestDto requestDto) {
        UserBasicInfoResponseDto response = identityService.signUp(requestDto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<UserBasicInfoResponseDto> verifyOtp(
            @RequestParam String phone,
            @RequestParam String code) {
        UserBasicInfoResponseDto response = identityService.verifyOtp(phone, code);
        return ResponseEntity.ok(response);
    }
}
