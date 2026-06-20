package com.prishtha.mvp.identity.api.controller;

import com.prishtha.mvp.identity.api.contract.AuthorProfileService;
import com.prishtha.mvp.identity.api.dto.request.AuthorProfileUpdateRequestDto;
import com.prishtha.mvp.identity.api.dto.response.AuthorProfileResponseDto;
import com.prishtha.mvp.identity.api.dto.response.PublicAuthorProfileResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/authors")
@RequiredArgsConstructor
public class AuthorProfileController {

    private final AuthorProfileService authorProfileService;

    @GetMapping("/me")
    public ResponseEntity<AuthorProfileResponseDto> getMyAuthorProfile(@RequestParam Long requesterUserId) {
        return ResponseEntity.ok(authorProfileService.getMyAuthorProfile(requesterUserId));
    }

    @PatchMapping("/me")
    public ResponseEntity<AuthorProfileResponseDto> updateMyAuthorProfile(
            @RequestParam Long requesterUserId, @RequestBody AuthorProfileUpdateRequestDto requestDto) {
        return ResponseEntity.ok(authorProfileService.updateMyAuthorProfile(requesterUserId, requestDto));
    }

    @GetMapping("/{authorProfileId}")
    public ResponseEntity<PublicAuthorProfileResponseDto> getPublicAuthorProfile(
            @PathVariable Long authorProfileId) {
        return ResponseEntity.ok(authorProfileService.getPublicAuthorProfile(authorProfileId));
    }
}
