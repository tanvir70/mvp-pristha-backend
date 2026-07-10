package com.prishtha.mvp.catalog.internal.service;

import com.prishtha.mvp.catalog.api.contract.CatalogService;
import com.prishtha.mvp.catalog.api.dto.response.PostDetailResponseDto;
import com.prishtha.mvp.catalog.api.dto.response.PostSummaryResponseDto;
import com.prishtha.mvp.catalog.internal.entity.PublishedWriting;
import com.prishtha.mvp.catalog.internal.repository.PublishedWritingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class CatalogServiceImpl implements CatalogService {

    private final PublishedWritingRepository publishedWritingRepository;

    @Override
    public Page<PostSummaryResponseDto> getPublishedPosts(String query, String tagSlug, Pageable pageable) {
        Page<PublishedWriting> writings;
        if (tagSlug != null && !tagSlug.isBlank()) {
            writings = publishedWritingRepository.findByTag(tagSlug.trim(), pageable);
        } else if (query == null || query.isBlank()) {
            writings = publishedWritingRepository.findAllByOrderByPublishedAtDesc(pageable);
        } else {
            writings = publishedWritingRepository.search(query.trim(), pageable);
        }
        return writings.map(this::toSummaryDto);
    }

    @Override
    @Transactional
    public PostDetailResponseDto getPublishedPostBySlug(String slug) {
        PublishedWriting writing = publishedWritingRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Published post not found"));
        publishedWritingRepository.incrementViewCount(writing.getId());
        return toDetailDto(writing);
    }

    private PostSummaryResponseDto toSummaryDto(PublishedWriting writing) {
        return PostSummaryResponseDto.builder()
                .id(writing.getId())
                .slug(writing.getSlug())
                .title(writing.getTitle())
                .synopsis(writing.getSynopsis())
                .coverImageUrl(writing.getCoverImageUrl())
                .priceType(writing.getPriceType())
                .priceAmount(writing.getPriceAmount())
                .authorId(writing.getAuthorId())
                .authorPenName(writing.getAuthorPenName())
                .publishedAt(writing.getPublishedAt())
                .viewCount(writing.getViewCount())
                .likeCount(writing.getLikeCount())
                .commentCount(writing.getCommentCount())
                .build();
    }

    private PostDetailResponseDto toDetailDto(PublishedWriting writing) {
        return PostDetailResponseDto.builder()
                .id(writing.getId())
                .slug(writing.getSlug())
                .title(writing.getTitle())
                .synopsis(writing.getSynopsis())
                .coverImageUrl(writing.getCoverImageUrl())
                .priceType(writing.getPriceType())
                .priceAmount(writing.getPriceAmount())
                .authorId(writing.getAuthorId())
                .authorPenName(writing.getAuthorPenName())
                .publishedAt(writing.getPublishedAt())
                .viewCount(writing.getViewCount())
                .likeCount(writing.getLikeCount())
                .commentCount(writing.getCommentCount())
                .build();
    }
}
