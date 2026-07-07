package com.prishtha.mvp.catalog.api.dto.request;

import com.prishtha.mvp.catalog.internal.entity.PostStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPostReviewRequestDto {
    private PostStatus status;
    private String reviewNote;
}
