package com.localdelivery.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AcceptRequestDto(@NotNull @Positive Long helperId) {}
