package com.localdelivery.exception;

public class RequestNotOpenException extends RuntimeException {
    public RequestNotOpenException() {
        super("Delivery request is no longer open for acceptance.");
    }
}
