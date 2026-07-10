package com.prishtha.mvp.studio.api.contract;

import com.prishtha.mvp.studio.api.dto.request.AssignWritingCategoriesRequestDto;
import com.prishtha.mvp.studio.api.dto.request.AuthorWritingUpsertRequestDto;
import com.prishtha.mvp.studio.api.dto.response.AuthorWritingResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuthorWritingService {

    AuthorWritingResponseDto createDraft(Long authorProfileId, AuthorWritingUpsertRequestDto requestDto);

    AuthorWritingResponseDto updateDraft(
            Long authorProfileId, Long writingId, AuthorWritingUpsertRequestDto requestDto);

    Page<AuthorWritingResponseDto> getMyWritings(Long authorProfileId, Pageable pageable);

    AuthorWritingResponseDto getMyWritingById(Long authorProfileId, Long writingId);

    void softDeleteMyWriting(Long authorProfileId, Long writingId);

    AuthorWritingResponseDto publish(Long authorProfileId, Long writingId);

    AuthorWritingResponseDto unpublish(Long authorProfileId, Long writingId);

    AuthorWritingResponseDto assignCategories(
            Long authorProfileId, Long writingId, AssignWritingCategoriesRequestDto requestDto);
}
