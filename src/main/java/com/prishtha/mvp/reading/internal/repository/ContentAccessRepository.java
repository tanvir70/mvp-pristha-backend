package com.prishtha.mvp.reading.internal.repository;

import com.prishtha.mvp.reading.internal.entity.ContentAccess;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentAccessRepository extends JpaRepository<ContentAccess, Long> {
    Optional<ContentAccess> findByReaderIdAndWritingId(Long readerId, Long writingId);

    boolean existsByReaderIdAndWritingId(Long readerId, Long writingId);
}
