package com.prishtha.mvp.catalog.internal.service;

import com.prishtha.mvp.catalog.api.contract.AuthorPostService;
import com.prishtha.mvp.catalog.api.dto.request.AssignPostTagsRequestDto;
import com.prishtha.mvp.catalog.api.dto.request.AuthorPostUpsertRequestDto;
import com.prishtha.mvp.catalog.api.dto.response.AuthorPostResponseDto;
import com.prishtha.mvp.identity.api.contract.AuthorProfileService;
import com.prishtha.mvp.catalog.internal.entity.Post;
import com.prishtha.mvp.catalog.internal.entity.PostTag;
import com.prishtha.mvp.catalog.internal.entity.PostTagId;
import com.prishtha.mvp.catalog.internal.entity.PostStatus;
import com.prishtha.mvp.catalog.internal.entity.PricingType;
import com.prishtha.mvp.catalog.internal.entity.Tag;
import com.prishtha.mvp.catalog.internal.repository.PostTagRepository;
import com.prishtha.mvp.catalog.internal.repository.PostRepository;
import com.prishtha.mvp.catalog.internal.repository.TagRepository;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
class AuthorPostServiceImpl implements AuthorPostService {

    private final PostRepository postRepository;
    private final TagRepository tagRepository;
    private final PostTagRepository postTagRepository;
    private final AuthorProfileService authorProfileService;

    @Override
    public AuthorPostResponseDto createDraftPost(Long authorProfileId, AuthorPostUpsertRequestDto requestDto) {
        authorProfileService.ensureAuthorIsActive(authorProfileId);
        validateBody(requestDto.getBody());
        validatePricing(requestDto.getPricingType(), requestDto.getPriceAmount());

        Post post = new Post();
        post.setAuthorId(authorProfileId);
        post.setTitle(requestDto.getTitle());
        post.setExcerpt(requestDto.getExcerpt());
        post.setBody(requestDto.getBody());
        post.setPreviewBody(requestDto.getPreviewBody());
        post.setCoverImageUrl(requestDto.getCoverImageUrl());
        post.setPricingType(resolvePricingType(requestDto.getPricingType()));
        post.setPriceAmount(requestDto.getPricingType() == PricingType.FREE ? null : requestDto.getPriceAmount());
        post.setBodyPlainText(extractPlainText(requestDto.getBody()));
        post.setStatus(PostStatus.DRAFT);
        post.setSlug(generateUniqueSlug(requestDto.getTitle()));

        return toDto(postRepository.save(post));
    }

    @Override
    public AuthorPostResponseDto updateDraftPost(
            Long authorProfileId, Long postId, AuthorPostUpsertRequestDto requestDto) {
        authorProfileService.ensureAuthorIsActive(authorProfileId);
        Post post = findMyPost(authorProfileId, postId);
        if (post.getStatus() != PostStatus.DRAFT) {
            throw new IllegalArgumentException("Only draft posts can be edited");
        }

        validateBody(requestDto.getBody());
        validatePricing(requestDto.getPricingType(), requestDto.getPriceAmount());

        post.setTitle(requestDto.getTitle());
        post.setExcerpt(requestDto.getExcerpt());
        post.setBody(requestDto.getBody());
        post.setPreviewBody(requestDto.getPreviewBody());
        post.setCoverImageUrl(requestDto.getCoverImageUrl());
        post.setPricingType(resolvePricingType(requestDto.getPricingType()));
        post.setPriceAmount(requestDto.getPricingType() == PricingType.FREE ? null : requestDto.getPriceAmount());
        post.setBodyPlainText(extractPlainText(requestDto.getBody()));
        post.setSlug(generateUniqueSlugForUpdate(requestDto.getTitle(), post.getSlug()));

        return toDto(postRepository.save(post));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuthorPostResponseDto> getMyPosts(Long authorProfileId, Pageable pageable) {
        return postRepository.findByAuthorIdAndDeletedAtIsNullOrderByUpdatedAtDesc(authorProfileId, pageable)
                .map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorPostResponseDto getMyPostById(Long authorProfileId, Long postId) {
        return toDto(findMyPost(authorProfileId, postId));
    }

    @Override
    public void softDeleteMyPost(Long authorProfileId, Long postId) {
        authorProfileService.ensureAuthorIsActive(authorProfileId);
        Post post = findMyPost(authorProfileId, postId);
        post.setDeletedAt(Instant.now());
        postRepository.save(post);
    }

    @Override
    public AuthorPostResponseDto publishPost(Long authorProfileId, Long postId) {
        authorProfileService.ensureAuthorIsActive(authorProfileId);
        Post post = findMyPost(authorProfileId, postId);
        if (post.getStatus() == PostStatus.PUBLISHED) {
            throw new IllegalArgumentException("Post is already published");
        }
        if (post.getDeletedAt() != null) {
            throw new IllegalArgumentException("Deleted posts cannot be published");
        }

        validateBody(post.getBody());
        validatePricing(post.getPricingType(), post.getPriceAmount());

        post.setStatus(PostStatus.PUBLISHED);
        post.setPublishedAt(Instant.now());
        return toDto(postRepository.save(post));
    }

    @Override
    public AuthorPostResponseDto unpublishPost(Long authorProfileId, Long postId) {
        authorProfileService.ensureAuthorIsActive(authorProfileId);
        Post post = findMyPost(authorProfileId, postId);
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new IllegalArgumentException("Only published posts can be unpublished");
        }

        post.setStatus(PostStatus.DRAFT);
        post.setPublishedAt(null);
        return toDto(postRepository.save(post));
    }

    @Override
    public AuthorPostResponseDto assignTags(
            Long authorProfileId, Long postId, AssignPostTagsRequestDto requestDto) {
        authorProfileService.ensureAuthorIsActive(authorProfileId);
        Post post = findMyPost(authorProfileId, postId);
        List<String> requestedSlugs = requestDto == null || requestDto.getTagSlugs() == null
                ? Collections.emptyList()
                : requestDto.getTagSlugs().stream()
                        .filter(slug -> slug != null && !slug.isBlank())
                        .map(String::trim)
                        .toList();

        List<String> uniqueSlugs = new java.util.ArrayList<>(new HashSet<>(requestedSlugs));
        List<Tag> tags = uniqueSlugs.isEmpty() ? Collections.emptyList() : tagRepository.findBySlugIn(uniqueSlugs);

        if (tags.size() != uniqueSlugs.size()) {
            throw new IllegalArgumentException("One or more tag slugs are invalid");
        }

        postTagRepository.deleteByPost_Id(post.getId());
        for (Tag tag : tags) {
            PostTag postTag = new PostTag();
            postTag.setId(new PostTagId(post.getId(), tag.getId()));
            postTag.setPost(post);
            postTag.setTag(tag);
            postTagRepository.save(postTag);
        }

        return toDto(post);
    }

    private Post findMyPost(Long authorProfileId, Long postId) {
        return postRepository.findByIdAndAuthorIdAndDeletedAtIsNull(postId, authorProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found for this author"));
    }

    private PricingType resolvePricingType(PricingType pricingType) {
        return pricingType == null ? PricingType.FREE : pricingType;
    }

    private void validateBody(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Post body must not be empty");
        }
    }

    private void validatePricing(PricingType pricingType, java.math.BigDecimal priceAmount) {
        PricingType resolvedPricingType = resolvePricingType(pricingType);
        if (resolvedPricingType == PricingType.FREE && priceAmount != null) {
            throw new IllegalArgumentException("FREE posts cannot have a price");
        }
        if (resolvedPricingType == PricingType.LOCKED
                && (priceAmount == null || priceAmount.compareTo(java.math.BigDecimal.ONE) < 0)) {
            throw new IllegalArgumentException("LOCKED posts require priceAmount >= 1");
        }
    }

    private String extractPlainText(String body) {
        // MVP fallback: store compact text for simple LIKE search.
        return body.replaceAll("\\s+", " ").trim();
    }

    private String generateUniqueSlug(String title) {
        String baseSlug = slugify(title);
        String candidate = baseSlug;
        int counter = 2;
        while (postRepository.existsBySlug(candidate)) {
            candidate = baseSlug + "-" + counter++;
        }
        return candidate;
    }

    private String generateUniqueSlugForUpdate(String title, String currentSlug) {
        String baseSlug = slugify(title);
        if (baseSlug.equals(currentSlug)) {
            return currentSlug;
        }
        String candidate = baseSlug;
        int counter = 2;
        while (postRepository.existsBySlug(candidate) && !candidate.equals(currentSlug)) {
            candidate = baseSlug + "-" + counter++;
        }
        return candidate;
    }

    private String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "post-" + System.currentTimeMillis();
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "post-" + System.currentTimeMillis() : normalized;
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
