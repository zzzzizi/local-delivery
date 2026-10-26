package com.localdelivery.exception;

public class InvalidAccountTokenException extends RuntimeException {
    public InvalidAccountTokenException() { super("This link is invalid or expired. Request a new email and try again."); }
}
