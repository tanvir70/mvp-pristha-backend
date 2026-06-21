package com.prishtha.mvp.catalog.api.contract;

import com.prishtha.mvp.catalog.api.dto.response.MediaUploadResponseDto;
import org.springframework.web.multipart.MultipartFile;

public interface MediaUploadService {

    MediaUploadResponseDto uploadImage(Long authorProfileId, MultipartFile file);
}
