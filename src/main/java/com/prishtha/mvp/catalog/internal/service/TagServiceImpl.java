package com.prishtha.mvp.catalog.internal.service;

import com.prishtha.mvp.catalog.api.contract.TagService;
import com.prishtha.mvp.catalog.api.dto.response.TagResponseDto;
import com.prishtha.mvp.studio.api.contract.CategoryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class TagServiceImpl implements TagService {

    private final CategoryService categoryService;

    @Override
    public List<TagResponseDto> getAllTags() {
        return categoryService.getAllCategories().stream()
                .map(category -> TagResponseDto.builder()
                        .id(category.id())
                        .name(category.name())
                        .build())
                .toList();
    }
}
