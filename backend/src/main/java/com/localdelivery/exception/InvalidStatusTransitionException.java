package com.localdelivery.exception;

import com.localdelivery.model.DeliveryStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(DeliveryStatus current, DeliveryStatus next) {
        this("Cannot change delivery status from " + current + " to " + next + ".");
    }

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
