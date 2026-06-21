package com.prishtha.mvp.catalog.api.contract;

import com.prishtha.mvp.catalog.api.dto.request.AdminPostReviewRequestDto;
import com.prishtha.mvp.catalog.api.dto.response.AuthorPostResponseDto;

public interface AdminPostService {

    AuthorPostResponseDto reviewPost(Long postId, AdminPostReviewRequestDto requestDto);
}
