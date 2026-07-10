package com.prishtha.mvp.studio.internal.service;

import com.prishtha.mvp.identity.api.contract.AuthorProfileService;
import com.prishtha.mvp.studio.api.contract.AuthorWritingService;
import com.prishtha.mvp.studio.api.dto.request.AssignWritingCategoriesRequestDto;
import com.prishtha.mvp.studio.api.dto.request.AuthorWritingUpsertRequestDto;
import com.prishtha.mvp.studio.api.dto.response.AuthorWritingResponseDto;
import com.prishtha.mvp.studio.api.event.WritingPublishedEvent;
import com.prishtha.mvp.studio.api.event.WritingUnpublishedEvent;
import com.prishtha.mvp.studio.internal.entity.Category;
import com.prishtha.mvp.studio.internal.entity.Writing;
import com.prishtha.mvp.studio.internal.entity.WritingCategory;
import com.prishtha.mvp.studio.internal.entity.WritingCategoryId;
import com.prishtha.mvp.studio.internal.enums.PriceType;
import com.prishtha.mvp.studio.internal.enums.WritingStatus;
import com.prishtha.mvp.studio.internal.enums.WritingType;
import com.prishtha.mvp.studio.internal.repository.CategoryRepository;
import com.prishtha.mvp.studio.internal.repository.WritingCategoryRepository;
import com.prishtha.mvp.studio.internal.repository.WritingRepository;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
class AuthorWritingServiceImpl implements AuthorWritingService {

    // ponytail: single-tenant MVP (tenant.tenants seeds exactly one row, id=1);
    // swap for real tenant resolution (JWT claim / TenantService) if multi-tenant ships.
    private static final Long DEFAULT_TENANT_ID = 1L;

    private final WritingRepository writingRepository;
    private final CategoryRepository categoryRepository;
    private final WritingCategoryRepository writingCategoryRepository;
    private final AuthorProfileService authorProfileService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public AuthorWritingResponseDto createDraft(Long authorProfileId, AuthorWritingUpsertRequestDto requestDto) {
        authorProfileService.ensureAuthorIsActive(authorProfileId);
        WritingType type = requestDto.getType() == null ? WritingType.POST : requestDto.getType();
        validateParent(type, requestDto.getParentId());
        validateBody(requestDto.getBodyJson());
        validatePricing(requestDto.getPriceType(), requestDto.getPriceAmount());

        Writing writing = new Writing();
        writing.setTenantId(DEFAULT_TENANT_ID);
        writing.setAuthorId(authorProfileId);
        applyUpsert(writing, requestDto, type);

        return toDto(writingRepository.save(writing));
    }

    @Override
    public AuthorWritingResponseDto updateDraft(
            Long authorProfileId, Long writingId, AuthorWritingUpsertRequestDto requestDto) {
        authorProfileService.ensureAuthorIsActive(authorProfileId);
        Writing writing = findMyWriting(authorProfileId, writingId);
        if (writing.getStatus() != WritingStatus.DRAFT) {
            throw new IllegalArgumentException("Only draft writings can be edited");
        }

        WritingType type = requestDto.getType() == null ? writing.getType() : requestDto.getType();
        validateParent(type, requestDto.getParentId());
        validateBody(requestDto.getBodyJson());
        validatePricing(requestDto.getPriceType(), requestDto.getPriceAmount());

        applyUpsert(writing, requestDto, type);

        return toDto(writingRepository.save(writing));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuthorWritingResponseDto> getMyWritings(Long authorProfileId, Pageable pageable) {
        return writingRepository.findByAuthorIdAndDeletedAtIsNullOrderByUpdatedAtDesc(authorProfileId, pageable)
                .map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorWritingResponseDto getMyWritingById(Long authorProfileId, Long writingId) {
        return toDto(findMyWriting(authorProfileId, writingId));
    }

    @Override
    public void softDeleteMyWriting(Long authorProfileId, Long writingId) {
        authorProfileService.ensureAuthorIsActive(authorProfileId);
        Writing writing = findMyWriting(authorProfileId, writingId);
        writing.setDeletedAt(Instant.now());
        writingRepository.save(writing);
        if (writing.getStatus() == WritingStatus.PUBLISHED) {
            eventPublisher.publishEvent(new WritingUnpublishedEvent(writing.getId()));
        }
    }

    @Override
    public AuthorWritingResponseDto publish(Long authorProfileId, Long writingId) {
        authorProfileService.ensureAuthorIsActive(authorProfileId);
        Writing writing = findMyWriting(authorProfileId, writingId);
        if (writing.getStatus() == WritingStatus.PUBLISHED) {
            throw new IllegalArgumentException("Writing is already published");
        }

        validateBody(writing.getBodyJson());
        validatePricing(writing.getPriceType(), writing.getPriceAmount());

        if (writing.getSlug() == null) {
            writing.setSlug(generateUniqueSlug(writing.getTitle()));
        }
        writing.setStatus(WritingStatus.PUBLISHED);
        Writing saved = writingRepository.save(writing);

        List<String> categoryNames = writingCategoryRepository.findByIdWritingId(writing.getId()).stream()
                .map(wc -> wc.getCategory().getName())
                .toList();

        eventPublisher.publishEvent(new WritingPublishedEvent(
                saved.getId(),
                saved.getTenantId(),
                saved.getAuthorId(),
                saved.getParent() == null ? null : saved.getParent().getId(),
                saved.getTitle(),
                saved.getSlug(),
                saved.getSynopsis(),
                saved.getCoverImageUrl(),
                saved.getPreviewJson(),
                saved.getType().name(),
                saved.getStatus().name(),
                saved.getPriceType().name(),
                saved.getPriceAmount(),
                categoryNames,
                Instant.now()));

        return toDto(saved);
    }

    @Override
    public AuthorWritingResponseDto unpublish(Long authorProfileId, Long writingId) {
        authorProfileService.ensureAuthorIsActive(authorProfileId);
        Writing writing = findMyWriting(authorProfileId, writingId);
        if (writing.getStatus() != WritingStatus.PUBLISHED) {
            throw new IllegalArgumentException("Only published writings can be unpublished");
        }

        writing.setStatus(WritingStatus.DRAFT);
        Writing saved = writingRepository.save(writing);
        eventPublisher.publishEvent(new WritingUnpublishedEvent(saved.getId()));

        return toDto(saved);
    }

    @Override
    public AuthorWritingResponseDto assignCategories(
            Long authorProfileId, Long writingId, AssignWritingCategoriesRequestDto requestDto) {
        authorProfileService.ensureAuthorIsActive(authorProfileId);
        Writing writing = findMyWriting(authorProfileId, writingId);

        List<String> requestedNames = requestDto == null || requestDto.getCategoryNames() == null
                ? Collections.emptyList()
                : requestDto.getCategoryNames().stream()
                        .filter(name -> name != null && !name.isBlank())
                        .map(String::trim)
                        .toList();

        List<String> uniqueNames = new java.util.ArrayList<>(new HashSet<>(requestedNames));
        List<Category> categories = uniqueNames.isEmpty()
                ? Collections.emptyList()
                : categoryRepository.findByNameIn(uniqueNames);

        if (categories.size() != uniqueNames.size()) {
            throw new IllegalArgumentException("One or more category names are invalid");
        }

        writingCategoryRepository.deleteByIdWritingId(writing.getId());
        for (Category category : categories) {
            WritingCategory writingCategory = new WritingCategory();
            writingCategory.setId(new WritingCategoryId(writing.getId(), category.getId()));
            writingCategory.setWriting(writing);
            writingCategory.setCategory(category);
            writingCategoryRepository.save(writingCategory);
        }

        return toDto(writing);
    }

    private void applyUpsert(Writing writing, AuthorWritingUpsertRequestDto requestDto, WritingType type) {
        writing.setTitle(requestDto.getTitle());
        writing.setSynopsis(requestDto.getSynopsis());
        writing.setBodyJson(requestDto.getBodyJson());
        writing.setPreviewJson(requestDto.getPreviewJson());
        writing.setCoverImageUrl(requestDto.getCoverImageUrl());
        writing.setType(type);
        writing.setParent(resolveParent(requestDto.getParentId()));
        writing.setPriceType(resolvePriceType(requestDto.getPriceType()));
        writing.setPriceAmount(requestDto.getPriceType() == PriceType.LOCKED
                ? requestDto.getPriceAmount()
                : BigDecimal.ZERO);
    }

    private Writing resolveParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        return writingRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Parent writing not found"));
    }

    private Writing findMyWriting(Long authorProfileId, Long writingId) {
        return writingRepository.findByIdAndAuthorIdAndDeletedAtIsNull(writingId, authorProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Writing not found for this author"));
    }

    private PriceType resolvePriceType(PriceType priceType) {
        return priceType == null ? PriceType.FREE : priceType;
    }

    private void validateParent(WritingType type, Long parentId) {
        if (parentId != null && type != WritingType.CHAPTER) {
            throw new IllegalArgumentException("Only chapters can have a parent writing");
        }
    }

    private void validateBody(String bodyJson) {
        if (bodyJson == null || bodyJson.isBlank()) {
            throw new IllegalArgumentException("Writing body must not be empty");
        }
    }

    private void validatePricing(PriceType priceType, BigDecimal priceAmount) {
        PriceType resolved = resolvePriceType(priceType);
        if (resolved == PriceType.LOCKED
                && (priceAmount == null || priceAmount.compareTo(BigDecimal.ONE) < 0)) {
            throw new IllegalArgumentException("LOCKED writings require priceAmount >= 1");
        }
    }

    private String generateUniqueSlug(String title) {
        String baseSlug = slugify(title);
        String candidate = baseSlug;
        int counter = 2;
        while (writingRepository.existsBySlug(candidate)) {
            candidate = baseSlug + "-" + counter++;
        }
        return candidate;
    }

    private String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "writing-" + System.currentTimeMillis();
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "writing-" + System.currentTimeMillis() : normalized;
    }

    private AuthorWritingResponseDto toDto(Writing writing) {
        return AuthorWritingResponseDto.builder()
                .id(writing.getId())
                .authorId(writing.getAuthorId())
                .parentId(writing.getParent() == null ? null : writing.getParent().getId())
                .slug(writing.getSlug())
                .title(writing.getTitle())
                .synopsis(writing.getSynopsis())
                .bodyJson(writing.getBodyJson())
                .previewJson(writing.getPreviewJson())
                .coverImageUrl(writing.getCoverImageUrl())
                .type(writing.getType().name())
                .priceType(writing.getPriceType().name())
                .priceAmount(writing.getPriceAmount())
                .status(writing.getStatus().name())
                .createdAt(writing.getCreatedAt())
                .updatedAt(writing.getUpdatedAt())
                .deletedAt(writing.getDeletedAt())
                .build();
    }
}
