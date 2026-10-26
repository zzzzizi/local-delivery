package com.localdelivery.dto;

import jakarta.validation.constraints.Positive;

/** Optional legacy ID is checked against the session; new clients send an empty object. */
public record AcceptRequestDto(@Positive Long helperId) {}
