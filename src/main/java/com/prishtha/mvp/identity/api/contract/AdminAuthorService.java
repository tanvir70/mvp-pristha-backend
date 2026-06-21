package com.prishtha.mvp.identity.api.contract;

import com.prishtha.mvp.identity.api.dto.response.AuthorProfileResponseDto;

public interface AdminAuthorService {

    AuthorProfileResponseDto deactivateAuthor(Long authorProfileId, Long adminUserId);

    AuthorProfileResponseDto activateAuthor(Long authorProfileId, Long adminUserId);
}
