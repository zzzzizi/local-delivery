package com.localdelivery.dto;

import com.localdelivery.model.DeliveryStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull DeliveryStatus status) {}
