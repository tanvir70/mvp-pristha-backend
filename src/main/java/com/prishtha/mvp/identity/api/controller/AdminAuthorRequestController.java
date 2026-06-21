package com.prishtha.mvp.identity.api.controller;

import com.prishtha.mvp.identity.api.contract.AuthorRequestService;
import com.prishtha.mvp.identity.api.dto.request.AuthorRequestRejectRequestDto;
import com.prishtha.mvp.identity.api.dto.response.AuthorRequestResponseDto;
import java.util.List;
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
@RequestMapping("/api/v1/admin/author-requests")
@RequiredArgsConstructor
public class AdminAuthorRequestController {

    private final AuthorRequestService authorRequestService;

    @GetMapping
    public ResponseEntity<List<AuthorRequestResponseDto>> getAuthorRequestsByStatus(
            @RequestParam(defaultValue = "PENDING") String status) {
        return ResponseEntity.ok(authorRequestService.getRequestsByStatus(status));
    }

    @PatchMapping("/{requestId}/approve")
    public ResponseEntity<AuthorRequestResponseDto> approveAuthorRequest(
            @PathVariable Long requestId, @RequestParam Long adminUserId) {
        return ResponseEntity.ok(authorRequestService.approveRequest(requestId, adminUserId));
    }

    @PatchMapping("/{requestId}/reject")
    public ResponseEntity<AuthorRequestResponseDto> rejectAuthorRequest(
            @PathVariable Long requestId,
            @RequestParam Long adminUserId,
            @RequestBody(required = false) AuthorRequestRejectRequestDto requestDto) {
        return ResponseEntity.ok(authorRequestService.rejectRequest(requestId, adminUserId, requestDto));
    }
}
