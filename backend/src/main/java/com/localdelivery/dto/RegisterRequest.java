package com.localdelivery.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 12, max = 128) String password) {
    public RegisterRequest {
        name = name == null ? null : name.strip();
        email = Emails.normalize(email);
    }
    @Override public String toString() { return "RegisterRequest[credentials redacted]"; }
}
