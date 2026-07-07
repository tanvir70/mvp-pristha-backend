package com.prishtha.mvp.reading.api.controller;

import static com.prishtha.mvp.reading.internal.util.constant.ReadingRouteConstant.CONTENT;
import static com.prishtha.mvp.reading.internal.util.constant.ReadingRouteConstant.POSTS_BASE_PATH;

import com.prishtha.mvp.reading.api.contract.ContentAccessService;
import com.prishtha.mvp.reading.api.dto.response.ContentAccessResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(POSTS_BASE_PATH)
@RequiredArgsConstructor
public class ContentAccessController {

    private final ContentAccessService contentAccessService;

    @GetMapping(CONTENT)
    public ContentAccessResponseDto getContent(
            @PathVariable String slug, @RequestParam(required = false) Long readerId) {
        return contentAccessService.getContent(slug, readerId);
    }
}
