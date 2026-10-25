package com.localdelivery.service;

import com.localdelivery.dto.CreateDeliveryRequestRequest;
import com.localdelivery.dto.AcceptRequestDto;
import com.localdelivery.dto.DeliveryRequestResponse;
import com.localdelivery.dto.UpdateStatusRequest;
import com.localdelivery.exception.InvalidStatusTransitionException;
import com.localdelivery.exception.RequestAlreadyAcceptedException;
import com.localdelivery.exception.ResourceNotFoundException;
import com.localdelivery.exception.RequestNotOpenException;
import com.localdelivery.exception.SelfAcceptanceException;
import com.localdelivery.model.DeliveryRequest;
import com.localdelivery.model.DeliveryStatus;
import com.localdelivery.repository.DeliveryRequestRepository;
import com.localdelivery.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.security.access.AccessDeniedException;
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
    public DeliveryRequestResponse create(CreateDeliveryRequestRequest input, Long actorId) {
        checkClaim(input.customerId(), actorId);
        var customer = users.findById(actorId)
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

    @Transactional(readOnly = true)
    public List<DeliveryRequestResponse> listByCustomer(Long customerId) {
        if (!users.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer not found.");
        }
        return requests.findByCustomer_IdOrderByCreatedAtDescIdDesc(customerId)
                .stream().map(DeliveryRequestResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DeliveryRequestResponse> listByHelper(Long helperId) {
        if (!users.existsById(helperId)) {
            throw new ResourceNotFoundException("Helper not found.");
        }
        return requests.findByHelper_IdOrderByCreatedAtDescIdDesc(helperId)
                .stream().map(DeliveryRequestResponse::from).toList();
    }

    @Transactional
    public DeliveryRequestResponse accept(Long id, AcceptRequestDto input, Long actorId) {
        var request = requests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery request not found."));
        if (request.getHelper() != null || request.getStatus() == DeliveryStatus.ACCEPTED) {
            throw new RequestAlreadyAcceptedException();
        }
        if (!request.getStatus().canTransitionTo(DeliveryStatus.ACCEPTED)) {
            throw new RequestNotOpenException();
        }
        checkClaim(input.helperId(), actorId);
        var helper = users.findById(actorId)
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

    @Transactional
    public DeliveryRequestResponse updateStatus(Long id, UpdateStatusRequest input, Long actorId) {
        var request = requests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery request not found."));
        var next = input.status();
        var ownerId = next == DeliveryStatus.CANCELLED ? request.getCustomer().getId()
                : request.getHelper() == null ? null : request.getHelper().getId();
        if (!actorId.equals(ownerId)) throw new AccessDeniedException("Request ownership required.");
        if (!request.getStatus().canTransitionTo(next)) {
            throw new InvalidStatusTransitionException(request.getStatus(), next);
        }
        // Acceptance must assign and validate a helper through the dedicated endpoint.
        if (next == DeliveryStatus.ACCEPTED) {
            throw new InvalidStatusTransitionException(
                    "Use POST /api/requests/{id}/accept to assign a helper and accept this request.");
        }
        if (next != DeliveryStatus.CANCELLED && request.getHelper() == null) {
            throw new InvalidStatusTransitionException("Delivery request has no assigned helper.");
        }
        request.setStatus(next);
        var saved = requests.saveAndFlush(request);
        entityManager.refresh(saved);
        return DeliveryRequestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public DeliveryRequestResponse get(Long id, Long actorId) {
        var request = requests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery request not found."));
        if (request.getStatus() != DeliveryStatus.OPEN && !request.getCustomer().getId().equals(actorId)
                && (request.getHelper() == null || !request.getHelper().getId().equals(actorId))) {
            throw new AccessDeniedException("Only request participants may view this request.");
        }
        return DeliveryRequestResponse.from(request);
    }

    public static void checkClaim(Long claimedId, Long actorId) {
        if (claimedId != null && !claimedId.equals(actorId))
            throw new AccessDeniedException("Identity must match the authenticated user.");
    }
}
