package com.prishtha.mvp.reading.api.contract;

import com.prishtha.mvp.reading.api.dto.response.ContentAccessResponseDto;

public interface ContentAccessService {

    ContentAccessResponseDto getContent(String slug, Long readerId);
}
