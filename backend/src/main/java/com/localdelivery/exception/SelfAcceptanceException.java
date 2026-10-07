package com.localdelivery.exception;

public class SelfAcceptanceException extends RuntimeException {
    public SelfAcceptanceException() {
        super("Customers cannot accept their own delivery requests.");
    }
}
