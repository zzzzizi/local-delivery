package com.localdelivery.exception;

public class RateLimitException extends RuntimeException {
    private final long retryAfter;
    public RateLimitException(long retryAfter) {
        super("Too many attempts. Please wait and try again.");
        this.retryAfter = retryAfter;
    }
    public long retryAfter() { return retryAfter; }
}
