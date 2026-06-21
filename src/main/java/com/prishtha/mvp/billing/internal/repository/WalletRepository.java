package com.prishtha.mvp.billing.internal.repository;

import com.prishtha.mvp.billing.internal.entity.Wallet;
import com.prishtha.mvp.billing.internal.enums.WalletType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByOwnerIdAndType(Long ownerId, WalletType type);
}
