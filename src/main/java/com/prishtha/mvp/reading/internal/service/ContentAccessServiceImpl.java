package com.prishtha.mvp.reading.internal.service;

import com.prishtha.mvp.reading.api.contract.AccessLevel;
import com.prishtha.mvp.reading.api.contract.ContentAccessService;
import com.prishtha.mvp.reading.api.dto.response.ContentAccessResponseDto;
import com.prishtha.mvp.reading.internal.repository.ContentAccessRepository;
import com.prishtha.mvp.studio.api.contract.WritingContentService;
import com.prishtha.mvp.studio.api.dto.response.WritingContentResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ContentAccessServiceImpl implements ContentAccessService {

    // ponytail: naive char-count truncation of the JSON body as a teaser when no
    // explicit previewJson was set at publish time. Swap for a TipTap/EditorJS-aware
    // truncator if guests start seeing broken JSON fragments client-side.
    private static final int PREVIEW_CHAR_LIMIT = 500;
    private static final String FREE_PRICE_TYPE = "FREE";

    private final WritingContentService writingContentService;
    private final ContentAccessRepository contentAccessRepository;

    @Override
    public ContentAccessResponseDto getContent(String slug, Long readerId) {
        WritingContentResponseDto writing = writingContentService.getPublishedContentBySlug(slug);

        boolean isFree = FREE_PRICE_TYPE.equals(writing.getPriceType());
        boolean hasUnlock = readerId != null
                && contentAccessRepository.existsByReaderIdAndWritingId(readerId, writing.getWritingId());

        if (isFree || hasUnlock) {
            return ContentAccessResponseDto.builder()
                    .slug(slug)
                    .accessLevel(AccessLevel.FULL)
                    .body(writing.getBodyJson())
                    .build();
        }

        return ContentAccessResponseDto.builder()
                .slug(slug)
                .accessLevel(AccessLevel.PREVIEW)
                .body(resolvePreview(writing))
                .build();
    }

    private String resolvePreview(WritingContentResponseDto writing) {
        if (writing.getPreviewJson() != null) {
            return writing.getPreviewJson();
        }
        String body = writing.getBodyJson();
        if (body == null) {
            return null;
        }
        return body.length() <= PREVIEW_CHAR_LIMIT ? body : body.substring(0, PREVIEW_CHAR_LIMIT);
    }
}
