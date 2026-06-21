package com.prishtha.mvp.catalog.internal.service;

import com.prishtha.mvp.catalog.api.contract.CatalogService;
import com.prishtha.mvp.catalog.api.dto.response.PostDetailResponseDto;
import com.prishtha.mvp.catalog.api.dto.response.PostSummaryResponseDto;
import com.prishtha.mvp.catalog.internal.entity.Post;
import com.prishtha.mvp.catalog.internal.entity.PostStatus;
import com.prishtha.mvp.catalog.internal.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class CatalogServiceImpl implements CatalogService {

    private final PostRepository postRepository;

    @Override
    public Page<PostSummaryResponseDto> getPublishedPosts(String query, String tagSlug, Pageable pageable) {
        Page<Post> posts;
        if (tagSlug != null && !tagSlug.isBlank()) {
            posts = postRepository.findPublishedPostsByTagSlug(PostStatus.PUBLISHED, tagSlug.trim(), pageable);
        } else if (query == null || query.isBlank()) {
            posts = postRepository.findByStatusAndDeletedAtIsNullOrderByPublishedAtDesc(
                    PostStatus.PUBLISHED, pageable);
        } else {
            posts = postRepository.searchPublishedPosts(PostStatus.PUBLISHED, query.trim(), pageable);
        }
        return posts.map(this::toSummaryDto);
    }

    @Override
    public PostDetailResponseDto getPublishedPostBySlug(String slug) {
        Post post = postRepository.findBySlugAndStatusAndDeletedAtIsNull(slug, PostStatus.PUBLISHED)
                .orElseThrow(() -> new IllegalArgumentException("Published post not found"));
        return toDetailDto(post);
    }

    private PostSummaryResponseDto toSummaryDto(Post post) {
        return PostSummaryResponseDto.builder()
                .id(post.getId())
                .slug(post.getSlug())
                .title(post.getTitle())
                .excerpt(post.getExcerpt())
                .coverImageUrl(post.getCoverImageUrl())
                .pricingType(post.getPricingType())
                .priceAmount(post.getPriceAmount())
                .authorId(post.getAuthorId())
                .publishedAt(post.getPublishedAt())
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .build();
    }

    private PostDetailResponseDto toDetailDto(Post post) {
        return PostDetailResponseDto.builder()
                .id(post.getId())
                .slug(post.getSlug())
                .title(post.getTitle())
                .excerpt(post.getExcerpt())
                .coverImageUrl(post.getCoverImageUrl())
                .pricingType(post.getPricingType())
                .priceAmount(post.getPriceAmount())
                .authorId(post.getAuthorId())
                .publishedAt(post.getPublishedAt())
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .build();
    }
}
