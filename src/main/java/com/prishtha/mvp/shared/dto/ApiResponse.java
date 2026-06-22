package com.prishtha.mvp.shared.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private Instant timestamp;
}
