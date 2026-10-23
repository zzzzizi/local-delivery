package com.localdelivery.repository;

import com.localdelivery.model.DeliveryRequest;
import com.localdelivery.model.DeliveryStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRequestRepository extends JpaRepository<DeliveryRequest, Long> {
    List<DeliveryRequest> findByStatusOrderByCreatedAtDescIdDesc(DeliveryStatus status);

    List<DeliveryRequest> findByCustomer_IdOrderByCreatedAtDescIdDesc(Long customerId);

    List<DeliveryRequest> findByHelper_IdOrderByCreatedAtDescIdDesc(Long helperId);
}
