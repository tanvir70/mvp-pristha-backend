package com.prishtha.mvp.studio.api.contract;

import com.prishtha.mvp.studio.api.dto.response.CategoryResponseDto;
import java.util.List;

public interface CategoryService {

    List<CategoryResponseDto> getAllCategories();
}
