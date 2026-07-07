package com.prishtha.mvp.catalog.api.controller;

import com.prishtha.mvp.catalog.api.contract.MediaUploadService;
import com.prishtha.mvp.catalog.api.dto.response.MediaUploadResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/author/media")
@RequiredArgsConstructor
public class MediaUploadController {

    private final MediaUploadService mediaUploadService;

    @PostMapping
    public ResponseEntity<MediaUploadResponseDto> uploadImage(
            @RequestParam Long authorProfileId, @RequestParam("file") MultipartFile file) {
        MediaUploadResponseDto response = mediaUploadService.uploadImage(authorProfileId, file);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
