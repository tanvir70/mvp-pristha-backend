package com.prishtha.mvp.billing.internal.repository;

import com.prishtha.mvp.billing.internal.entity.LedgerLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerLineRepository extends JpaRepository<LedgerLine, Long> {

    List<LedgerLine> findByWalletId(Long walletId);

    List<LedgerLine> findByJournalId(Long journalId);
}
