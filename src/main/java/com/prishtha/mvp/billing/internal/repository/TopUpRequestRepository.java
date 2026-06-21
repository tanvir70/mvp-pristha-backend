package com.prishtha.mvp.billing.internal.repository;

import com.prishtha.mvp.billing.internal.entity.TopUpRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopUpRequestRepository extends JpaRepository<TopUpRequest, Long> {

    List<TopUpRequest> findByUserId(Long userId);
}
