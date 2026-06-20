package com.prishtha.mvp.catalog.internal.repository;

import com.prishtha.mvp.catalog.internal.entity.Tag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findBySlug(String slug);

    List<Tag> findBySlugIn(List<String> slugs);
}
