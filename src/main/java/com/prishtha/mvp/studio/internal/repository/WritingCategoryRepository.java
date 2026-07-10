package com.prishtha.mvp.studio.internal.repository;

import com.prishtha.mvp.studio.internal.entity.WritingCategory;
import com.prishtha.mvp.studio.internal.entity.WritingCategoryId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WritingCategoryRepository extends JpaRepository<WritingCategory, WritingCategoryId> {
    List<WritingCategory> findByIdWritingId(Long writingId);

    List<WritingCategory> findByIdCategoryId(Long categoryId);

    void deleteByIdWritingId(Long writingId);
}
