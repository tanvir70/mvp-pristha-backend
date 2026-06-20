package com.prishtha.mvp.catalog.api.contract;

import com.prishtha.mvp.catalog.api.dto.request.AuthorPostUpsertRequestDto;
import com.prishtha.mvp.catalog.api.dto.response.AuthorPostResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuthorPostService {

    AuthorPostResponseDto createDraftPost(Long authorProfileId, AuthorPostUpsertRequestDto requestDto);

    AuthorPostResponseDto updateDraftPost(
            Long authorProfileId, Long postId, AuthorPostUpsertRequestDto requestDto);

    Page<AuthorPostResponseDto> getMyPosts(Long authorProfileId, Pageable pageable);

    AuthorPostResponseDto getMyPostById(Long authorProfileId, Long postId);

    void softDeleteMyPost(Long authorProfileId, Long postId);

    AuthorPostResponseDto publishPost(Long authorProfileId, Long postId);

    AuthorPostResponseDto unpublishPost(Long authorProfileId, Long postId);
}
