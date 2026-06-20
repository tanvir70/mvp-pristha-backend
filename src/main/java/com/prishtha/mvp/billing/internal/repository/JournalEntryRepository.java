package com.prishtha.mvp.billing.internal.repository;

import com.prishtha.mvp.billing.internal.entity.JournalEntry;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    Optional<JournalEntry> findByIdempotencyKey(String idempotencyKey);
}
