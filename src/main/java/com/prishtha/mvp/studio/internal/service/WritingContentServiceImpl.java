package com.prishtha.mvp.studio.internal.service;

import com.prishtha.mvp.shared.exception.EntityNotFoundException;
import com.prishtha.mvp.studio.api.contract.WritingContentService;
import com.prishtha.mvp.studio.api.dto.response.WritingContentResponseDto;
import com.prishtha.mvp.studio.internal.entity.Writing;
import com.prishtha.mvp.studio.internal.enums.WritingStatus;
import com.prishtha.mvp.studio.internal.repository.WritingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class WritingContentServiceImpl implements WritingContentService {

    private final WritingRepository writingRepository;

    @Override
    public WritingContentResponseDto getPublishedContentBySlug(String slug) {
        Writing writing = writingRepository.findBySlug(slug)
                .filter(w -> w.getDeletedAt() == null)
                .filter(w -> w.getStatus() == WritingStatus.PUBLISHED || w.getStatus() == WritingStatus.COMPLETED)
                .orElseThrow(() -> new EntityNotFoundException("Published writing not found for slug: " + slug));

        return WritingContentResponseDto.builder()
                .writingId(writing.getId())
                .slug(writing.getSlug())
                .priceType(writing.getPriceType().name())
                .bodyJson(writing.getBodyJson())
                .previewJson(writing.getPreviewJson())
                .build();
    }
}
