package com.localdelivery.dto;

import jakarta.validation.constraints.*;

public record EmailRequest(@NotBlank @Email @Size(max = 320) String email) {
    public EmailRequest { email = Emails.normalize(email); }
    @Override public String toString() { return "EmailRequest[redacted]"; }
}
