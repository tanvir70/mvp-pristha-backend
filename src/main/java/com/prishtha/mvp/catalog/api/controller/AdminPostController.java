package com.prishtha.mvp.catalog.api.controller;

import com.prishtha.mvp.catalog.api.contract.AdminPostService;
import com.prishtha.mvp.catalog.api.dto.request.AdminPostReviewRequestDto;
import com.prishtha.mvp.catalog.api.dto.response.AuthorPostResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

    private final AdminPostService adminPostService;

    @PatchMapping("/{postId}/review")
    public ResponseEntity<AuthorPostResponseDto> reviewPost(
            @PathVariable Long postId, @RequestBody AdminPostReviewRequestDto requestDto) {
        return ResponseEntity.ok(adminPostService.reviewPost(postId, requestDto));
    }
}
