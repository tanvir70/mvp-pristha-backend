package com.prishtha.mvp.identity.api.contract;

import com.prishtha.mvp.identity.api.dto.request.AuthorProfileUpdateRequestDto;
import com.prishtha.mvp.identity.api.dto.response.AuthorProfileResponseDto;
import com.prishtha.mvp.identity.api.dto.response.PublicAuthorProfileResponseDto;

public interface AuthorProfileService {

    AuthorProfileResponseDto getMyAuthorProfile(Long requesterUserId);

    AuthorProfileResponseDto updateMyAuthorProfile(
            Long requesterUserId, AuthorProfileUpdateRequestDto requestDto);

    PublicAuthorProfileResponseDto getPublicAuthorProfile(Long authorProfileId);
}
