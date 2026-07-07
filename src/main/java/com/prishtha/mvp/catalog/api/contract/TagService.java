package com.prishtha.mvp.catalog.api.contract;

import com.prishtha.mvp.catalog.api.dto.response.TagResponseDto;
import java.util.List;

public interface TagService {

    List<TagResponseDto> getAllTags();
}
