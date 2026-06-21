package com.prishtha.mvp.catalog.internal.service;

import com.prishtha.mvp.catalog.api.contract.AdminPostService;
import com.prishtha.mvp.catalog.api.dto.request.AdminPostReviewRequestDto;
import com.prishtha.mvp.catalog.api.dto.response.AuthorPostResponseDto;
import com.prishtha.mvp.catalog.internal.entity.Post;
import com.prishtha.mvp.catalog.internal.entity.PostStatus;
import com.prishtha.mvp.catalog.internal.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
class AdminPostServiceImpl implements AdminPostService {

    private final PostRepository postRepository;

    @Override
    public AuthorPostResponseDto reviewPost(Long postId, AdminPostReviewRequestDto requestDto) {
        if (requestDto == null || requestDto.getStatus() == null) {
            throw new IllegalArgumentException("Review status is required");
        }

        PostStatus targetStatus = requestDto.getStatus();
        if (targetStatus != PostStatus.UNDER_REVIEW && targetStatus != PostStatus.PUBLISHED) {
            throw new IllegalArgumentException("Admin review only supports UNDER_REVIEW or PUBLISHED");
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));

        if (post.getDeletedAt() != null) {
            throw new IllegalArgumentException("Deleted posts cannot be reviewed");
        }

        post.setStatus(targetStatus);
        if (targetStatus == PostStatus.UNDER_REVIEW) {
            post.setPublishedAt(null);
        }

        Post saved = postRepository.save(post);
        return toDto(saved);
    }

    private AuthorPostResponseDto toDto(Post post) {
        return AuthorPostResponseDto.builder()
                .id(post.getId())
                .authorId(post.getAuthorId())
                .slug(post.getSlug())
                .title(post.getTitle())
                .excerpt(post.getExcerpt())
                .body(post.getBody())
                .previewBody(post.getPreviewBody())
                .coverImageUrl(post.getCoverImageUrl())
                .pricingType(post.getPricingType())
                .priceAmount(post.getPriceAmount())
                .status(post.getStatus())
                .publishedAt(post.getPublishedAt())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .deletedAt(post.getDeletedAt())
                .build();
    }
}
