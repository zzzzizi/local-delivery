package com.localdelivery.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.localdelivery.model.RequestCategory;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreateDeliveryRequestRequest(
        @NotNull @Positive Long customerId,
        @NotNull RequestCategory category,
        @NotBlank @Size(max = 200) String title,
        @NotBlank String description,
        @NotBlank @Size(max = 500) String pickupAddress,
        @NotBlank @Size(max = 500) String deliveryAddress,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal shoppingBudget,
        @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal helperReward,
        @NotNull @Future @JsonDeserialize(using = OsloDeadlineDeserializer.class) OffsetDateTime deadline
) {
    public CreateDeliveryRequestRequest {
        title = trim(title);
        description = trim(description);
        pickupAddress = trim(pickupAddress);
        deliveryAddress = trim(deliveryAddress);
    }

    private static String trim(String value) {
        return value == null ? null : value.strip();
    }
}
