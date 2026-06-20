package com.prishtha.mvp.identity.api.controller;

import com.prishtha.mvp.identity.api.contract.AuthorRequestService;
import com.prishtha.mvp.identity.api.dto.request.AuthorRequestSubmitRequestDto;
import com.prishtha.mvp.identity.api.dto.response.AuthorRequestResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/author-requests")
@RequiredArgsConstructor
public class AuthorRequestController {

    private final AuthorRequestService authorRequestService;

    @PostMapping
    public ResponseEntity<AuthorRequestResponseDto> submitAuthorRequest(
            @RequestParam Long requesterUserId, @RequestBody AuthorRequestSubmitRequestDto requestDto) {
        AuthorRequestResponseDto response = authorRequestService.submitRequest(requesterUserId, requestDto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/me")
    public ResponseEntity<AuthorRequestResponseDto> getMyLatestRequest(@RequestParam Long requesterUserId) {
        return ResponseEntity.ok(authorRequestService.getMyLatestRequest(requesterUserId));
    }
}
