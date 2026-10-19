package com.localdelivery.exception;

public class RequestAlreadyAcceptedException extends RuntimeException {
    public RequestAlreadyAcceptedException() {
        super("Delivery request has already been accepted.");
    }
}
