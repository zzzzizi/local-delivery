package com.localdelivery.model;

public enum DeliveryStatus {
    OPEN, ACCEPTED, PICKED_UP, DELIVERING, DELIVERED, CANCELLED;

    public boolean canTransitionTo(DeliveryStatus next) {
        return switch (this) {
            case OPEN -> next == ACCEPTED || next == CANCELLED;
            case ACCEPTED -> next == PICKED_UP || next == CANCELLED;
            case PICKED_UP -> next == DELIVERING;
            case DELIVERING -> next == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
