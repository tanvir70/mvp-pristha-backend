package com.prishtha.mvp.catalog.api.controller;

import com.prishtha.mvp.catalog.api.contract.AuthorPostService;
import com.prishtha.mvp.catalog.api.dto.request.AuthorPostUpsertRequestDto;
import com.prishtha.mvp.catalog.api.dto.response.AuthorPostResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/author/posts")
@RequiredArgsConstructor
public class AuthorPostController {

    private final AuthorPostService authorPostService;

    @PostMapping
    public ResponseEntity<AuthorPostResponseDto> createDraftPost(
            @RequestParam Long authorProfileId, @RequestBody AuthorPostUpsertRequestDto requestDto) {
        AuthorPostResponseDto response = authorPostService.createDraftPost(authorProfileId, requestDto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{postId}")
    public ResponseEntity<AuthorPostResponseDto> updateDraftPost(
            @RequestParam Long authorProfileId,
            @PathVariable Long postId,
            @RequestBody AuthorPostUpsertRequestDto requestDto) {
        return ResponseEntity.ok(authorPostService.updateDraftPost(authorProfileId, postId, requestDto));
    }

    @GetMapping
    public Page<AuthorPostResponseDto> getMyPosts(
            @RequestParam Long authorProfileId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return authorPostService.getMyPosts(authorProfileId, pageable);
    }

    @GetMapping("/{postId}")
    public ResponseEntity<AuthorPostResponseDto> getMyPostById(
            @RequestParam Long authorProfileId, @PathVariable Long postId) {
        return ResponseEntity.ok(authorPostService.getMyPostById(authorProfileId, postId));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> softDeleteMyPost(
            @RequestParam Long authorProfileId, @PathVariable Long postId) {
        authorPostService.softDeleteMyPost(authorProfileId, postId);
        return ResponseEntity.noContent().build();
    }
}
