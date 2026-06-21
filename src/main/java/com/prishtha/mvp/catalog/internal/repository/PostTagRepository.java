package com.prishtha.mvp.catalog.internal.repository;

import com.prishtha.mvp.catalog.internal.entity.PostTag;
import com.prishtha.mvp.catalog.internal.entity.PostTagId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostTagRepository extends JpaRepository<PostTag, PostTagId> {

    void deleteByPost_Id(Long postId);

    List<PostTag> findByPost_Id(Long postId);
}
