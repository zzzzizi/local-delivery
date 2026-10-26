package com.localdelivery.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(@NotBlank @Email @Size(max = 320) String email,
                           @NotBlank @Size(max = 128) String password) {
    public LoginRequest {
        email = Emails.normalize(email);
    }
    @Override public String toString() { return "LoginRequest[credentials redacted]"; }
}
