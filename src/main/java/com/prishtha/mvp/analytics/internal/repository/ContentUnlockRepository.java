package com.prishtha.mvp.analytics.internal.repository;

import com.prishtha.mvp.analytics.internal.entity.ContentUnlock;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentUnlockRepository extends JpaRepository<ContentUnlock, Long> {

    List<ContentUnlock> findByWritingId(Long writingId);
}
