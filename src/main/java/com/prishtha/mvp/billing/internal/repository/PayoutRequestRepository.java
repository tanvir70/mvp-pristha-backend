package com.prishtha.mvp.billing.internal.repository;

import com.prishtha.mvp.billing.internal.entity.PayoutRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, Long> {

    List<PayoutRequest> findByAuthorId(Long authorId);
}
