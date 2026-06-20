package com.prishtha.mvp.catalog.api.controller;

import com.prishtha.mvp.catalog.api.contract.CatalogService;
import com.prishtha.mvp.catalog.api.dto.response.PostDetailResponseDto;
import com.prishtha.mvp.catalog.api.dto.response.PostSummaryResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping
    public Page<PostSummaryResponseDto> getPublishedPosts(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return catalogService.getPublishedPosts(q, pageable);
    }

    @GetMapping("/{slug}")
    public PostDetailResponseDto getPublishedPostBySlug(@PathVariable String slug) {
        return catalogService.getPublishedPostBySlug(slug);
    }
}
