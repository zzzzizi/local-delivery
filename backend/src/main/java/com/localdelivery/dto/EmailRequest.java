package com.localdelivery.dto;

import jakarta.validation.constraints.*;

public record EmailRequest(@NotBlank @Email @Size(max = 320) String email) {
    public EmailRequest { email = email == null ? null : email.strip().toLowerCase(java.util.Locale.ROOT); }
    @Override public String toString() { return "EmailRequest[redacted]"; }
}
