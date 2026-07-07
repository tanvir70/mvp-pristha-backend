package com.prishtha.mvp.reading.api.controller;

import static com.prishtha.mvp.reading.internal.util.constant.ReadingRouteConstant.CONTENT;
import static com.prishtha.mvp.reading.internal.util.constant.ReadingRouteConstant.POSTS_BASE_PATH;

import com.prishtha.mvp.reading.api.contract.ContentAccessService;
import com.prishtha.mvp.reading.api.dto.response.ContentAccessResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(POSTS_BASE_PATH)
@RequiredArgsConstructor
public class ContentAccessController {

    private final ContentAccessService contentAccessService;

    // Route is permitAll (guests read previews); jwt is null for guests and the
    // reader identity always comes from the verified token, never from the client.
    @GetMapping(CONTENT)
    public ContentAccessResponseDto getContent(@PathVariable String slug, @AuthenticationPrincipal Jwt jwt) {
        Long readerId = jwt == null ? null : Long.valueOf(jwt.getSubject());
        return contentAccessService.getContent(slug, readerId);
    }
}
