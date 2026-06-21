package com.prishtha.mvp.reading.internal.repository;

import com.prishtha.mvp.reading.internal.entity.LibraryEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryEntryRepository extends JpaRepository<LibraryEntry, Long> {
    Optional<LibraryEntry> findByReaderIdAndWritingId(Long readerId, Long writingId);

    List<LibraryEntry> findByReaderIdOrderByLastReadAtDesc(Long readerId);
}
