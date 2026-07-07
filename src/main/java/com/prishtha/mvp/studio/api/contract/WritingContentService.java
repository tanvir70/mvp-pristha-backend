package com.prishtha.mvp.studio.api.contract;

import com.prishtha.mvp.studio.api.dto.response.WritingContentResponseDto;

public interface WritingContentService {

    WritingContentResponseDto getPublishedContentBySlug(String slug);
}
