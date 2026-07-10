package com.prishtha.mvp.studio.internal.service;

import com.prishtha.mvp.studio.api.contract.CategoryService;
import com.prishtha.mvp.studio.api.dto.response.CategoryResponseDto;
import com.prishtha.mvp.studio.internal.repository.CategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    public List<CategoryResponseDto> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(category -> CategoryResponseDto.builder()
                        .id(category.getId())
                        .name(category.getName())
                        .build())
                .toList();
    }
}
