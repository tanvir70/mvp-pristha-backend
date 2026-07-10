package com.prishtha.mvp.catalog.api.dto.response;

import lombok.Builder;

@Builder
public record TagResponseDto(Long id, String name) {}
