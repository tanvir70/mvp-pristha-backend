package com.prishtha.mvp.studio.api.controller;

import static com.prishtha.mvp.studio.internal.util.constant.StudioRouteConstant.AUTHOR_POSTS_BASE_PATH;
import static com.prishtha.mvp.studio.internal.util.constant.StudioRouteConstant.BY_ID;
import static com.prishtha.mvp.studio.internal.util.constant.StudioRouteConstant.CATEGORIES;
import static com.prishtha.mvp.studio.internal.util.constant.StudioRouteConstant.PUBLISH;
import static com.prishtha.mvp.studio.internal.util.constant.StudioRouteConstant.UNPUBLISH;

import com.prishtha.mvp.studio.api.contract.AuthorWritingService;
import com.prishtha.mvp.studio.api.dto.request.AssignWritingCategoriesRequestDto;
import com.prishtha.mvp.studio.api.dto.request.AuthorWritingUpsertRequestDto;
import com.prishtha.mvp.studio.api.dto.response.AuthorWritingResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AUTHOR_POSTS_BASE_PATH)
@RequiredArgsConstructor
public class AuthorWritingController {

    private final AuthorWritingService authorWritingService;

    @PostMapping
    public ResponseEntity<AuthorWritingResponseDto> createDraft(
            @RequestParam Long authorProfileId, @RequestBody AuthorWritingUpsertRequestDto requestDto) {
        AuthorWritingResponseDto response = authorWritingService.createDraft(authorProfileId, requestDto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping(BY_ID)
    public ResponseEntity<AuthorWritingResponseDto> updateDraft(
            @RequestParam Long authorProfileId,
            @PathVariable Long writingId,
            @RequestBody AuthorWritingUpsertRequestDto requestDto) {
        return ResponseEntity.ok(authorWritingService.updateDraft(authorProfileId, writingId, requestDto));
    }

    @GetMapping
    public Page<AuthorWritingResponseDto> getMyWritings(
            @RequestParam Long authorProfileId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return authorWritingService.getMyWritings(authorProfileId, pageable);
    }

    @GetMapping(BY_ID)
    public ResponseEntity<AuthorWritingResponseDto> getMyWritingById(
            @RequestParam Long authorProfileId, @PathVariable Long writingId) {
        return ResponseEntity.ok(authorWritingService.getMyWritingById(authorProfileId, writingId));
    }

    @DeleteMapping(BY_ID)
    public ResponseEntity<Void> softDeleteMyWriting(
            @RequestParam Long authorProfileId, @PathVariable Long writingId) {
        authorWritingService.softDeleteMyWriting(authorProfileId, writingId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping(PUBLISH)
    public ResponseEntity<AuthorWritingResponseDto> publish(
            @RequestParam Long authorProfileId, @PathVariable Long writingId) {
        return ResponseEntity.ok(authorWritingService.publish(authorProfileId, writingId));
    }

    @PatchMapping(UNPUBLISH)
    public ResponseEntity<AuthorWritingResponseDto> unpublish(
            @RequestParam Long authorProfileId, @PathVariable Long writingId) {
        return ResponseEntity.ok(authorWritingService.unpublish(authorProfileId, writingId));
    }

    @PutMapping(CATEGORIES)
    public ResponseEntity<AuthorWritingResponseDto> assignCategories(
            @RequestParam Long authorProfileId,
            @PathVariable Long writingId,
            @RequestBody AssignWritingCategoriesRequestDto requestDto) {
        return ResponseEntity.ok(authorWritingService.assignCategories(authorProfileId, writingId, requestDto));
    }
}
