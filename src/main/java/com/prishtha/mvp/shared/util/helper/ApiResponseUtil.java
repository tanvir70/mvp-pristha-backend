package com.prishtha.mvp.shared.util.helper;

import com.prishtha.mvp.shared.dto.ApiResponse;
import java.time.Instant;

public final class ApiResponseUtil {

    private ApiResponseUtil() {
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(null, data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
}
