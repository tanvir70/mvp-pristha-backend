package com.prishtha.mvp.catalog.internal.repository;

import com.prishtha.mvp.catalog.internal.entity.PublishedWritingTag;
import com.prishtha.mvp.catalog.internal.entity.PublishedWritingTagId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublishedWritingTagRepository extends JpaRepository<PublishedWritingTag, PublishedWritingTagId> {
    List<PublishedWritingTag> findByIdWritingId(Long writingId);

    List<PublishedWritingTag> findByIdTag(String tag);

    void deleteByIdWritingId(Long writingId);
}
