package com.prishtha.mvp.catalog.api.contract;

import com.prishtha.mvp.catalog.api.dto.response.PostDetailResponseDto;
import com.prishtha.mvp.catalog.api.dto.response.PostSummaryResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CatalogService {

    Page<PostSummaryResponseDto> getPublishedPosts(String query, String tagSlug, Pageable pageable);

    PostDetailResponseDto getPublishedPostBySlug(String slug);
}
