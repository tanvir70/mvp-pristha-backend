package com.prishtha.mvp.catalog.api.controller;

import com.prishtha.mvp.catalog.api.contract.TagService;
import com.prishtha.mvp.catalog.api.dto.response.TagResponseDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public List<TagResponseDto> getAllTags() {
        return tagService.getAllTags();
    }
}
