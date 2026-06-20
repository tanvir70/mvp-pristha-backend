package com.prishtha.mvp.identity.api.controller;

import com.prishtha.mvp.identity.api.contract.AdminAuthorService;
import com.prishtha.mvp.identity.api.dto.response.AuthorProfileResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/authors")
@RequiredArgsConstructor
public class AdminAuthorController {

    private final AdminAuthorService adminAuthorService;

    @PatchMapping("/{authorProfileId}/deactivate")
    public ResponseEntity<AuthorProfileResponseDto> deactivateAuthor(
            @PathVariable Long authorProfileId, @RequestParam Long adminUserId) {
        return ResponseEntity.ok(adminAuthorService.deactivateAuthor(authorProfileId, adminUserId));
    }

    @PatchMapping("/{authorProfileId}/activate")
    public ResponseEntity<AuthorProfileResponseDto> activateAuthor(
            @PathVariable Long authorProfileId, @RequestParam Long adminUserId) {
        return ResponseEntity.ok(adminAuthorService.activateAuthor(authorProfileId, adminUserId));
    }
}
