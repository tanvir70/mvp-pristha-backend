package com.prishtha.mvp.analytics.internal.repository;

import com.prishtha.mvp.analytics.internal.entity.ContentView;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentViewRepository extends JpaRepository<ContentView, Long> {

    List<ContentView> findByWritingId(Long writingId);
}
