package com.localdelivery.service;

import com.localdelivery.dto.CreateDeliveryRequestRequest;
import com.localdelivery.dto.AcceptRequestDto;
import com.localdelivery.dto.DeliveryRequestResponse;
import com.localdelivery.exception.ResourceNotFoundException;
import com.localdelivery.exception.RequestNotOpenException;
import com.localdelivery.exception.SelfAcceptanceException;
import com.localdelivery.model.DeliveryRequest;
import com.localdelivery.model.DeliveryStatus;
import com.localdelivery.repository.DeliveryRequestRepository;
import com.localdelivery.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryRequestService {
    private final DeliveryRequestRepository requests;
    private final UserRepository users;
    private final EntityManager entityManager;

    public DeliveryRequestService(DeliveryRequestRepository requests, UserRepository users,
                                  EntityManager entityManager) {
        this.requests = requests;
        this.users = users;
        this.entityManager = entityManager;
    }

    @Transactional
    public DeliveryRequestResponse create(CreateDeliveryRequestRequest input) {
        var customer = users.findById(input.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        var request = new DeliveryRequest();
        request.setCustomer(customer);
        request.setCategory(input.category());
        request.setTitle(input.title());
        request.setDescription(input.description());
        request.setPickupAddress(input.pickupAddress());
        request.setDeliveryAddress(input.deliveryAddress());
        request.setShoppingBudget(input.shoppingBudget());
        request.setHelperReward(input.helperReward());
        request.setDeadline(input.deadline().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
        request.setStatus(DeliveryStatus.OPEN);
        var saved = requests.saveAndFlush(request);
        // Return PostgreSQL's stored decimal scale and timestamp precision.
        entityManager.refresh(saved);
        return DeliveryRequestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<DeliveryRequestResponse> listOpen() {
        return requests.findByStatusOrderByCreatedAtDescIdDesc(DeliveryStatus.OPEN)
                .stream().map(DeliveryRequestResponse::from).toList();
    }

    @Transactional
    public DeliveryRequestResponse accept(Long id, AcceptRequestDto input) {
        var request = requests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery request not found."));
        if (request.getStatus() != DeliveryStatus.OPEN || request.getHelper() != null) {
            throw new RequestNotOpenException();
        }
        var helper = users.findById(input.helperId())
                .orElseThrow(() -> new ResourceNotFoundException("Helper not found."));
        if (request.getCustomer().getId().equals(helper.getId())) {
            throw new SelfAcceptanceException();
        }
        request.setHelper(helper);
        request.setStatus(DeliveryStatus.ACCEPTED);
        // Flush checks @Version before returning; a competing update rolls this transaction back.
        var saved = requests.saveAndFlush(request);
        entityManager.refresh(saved);
        return DeliveryRequestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public DeliveryRequestResponse get(Long id) {
        return DeliveryRequestResponse.from(requests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery request not found.")));
    }
}
