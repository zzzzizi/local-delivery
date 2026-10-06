package com.localdelivery.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.localdelivery.model.DeliveryRequest;
import com.localdelivery.model.DeliveryStatus;
import com.localdelivery.model.RequestCategory;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record DeliveryRequestResponse(
        Long id,
        Long customerId,
        Long helperId,
        RequestCategory category,
        String title,
        String description,
        String pickupAddress,
        String deliveryAddress,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal shoppingBudget,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal helperReward,
        OffsetDateTime deadline,
        DeliveryStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static DeliveryRequestResponse from(DeliveryRequest request) {
        return new DeliveryRequestResponse(
                request.getId(), request.getCustomer().getId(),
                request.getHelper() == null ? null : request.getHelper().getId(),
                request.getCategory(), request.getTitle(), request.getDescription(),
                request.getPickupAddress(), request.getDeliveryAddress(),
                request.getShoppingBudget(), request.getHelperReward(),
                request.getDeadline().atOffset(ZoneOffset.UTC), request.getStatus(),
                request.getCreatedAt().atOffset(ZoneOffset.UTC),
                request.getUpdatedAt().atOffset(ZoneOffset.UTC));
    }
}
