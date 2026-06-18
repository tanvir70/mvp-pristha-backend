package com.prishtha.mvp.identity.api.contract;

import com.prishtha.mvp.identity.api.dto.request.UserSignUpRequestDto;
import com.prishtha.mvp.identity.api.dto.response.UserBasicInfoResponseDto;

public interface IdentityService {
    UserBasicInfoResponseDto signUp(UserSignUpRequestDto requestDto);
    UserBasicInfoResponseDto verifyOtp(String phone, String code);
}
