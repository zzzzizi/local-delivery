package com.localdelivery.dto;

import jakarta.validation.constraints.*;

public record ResetPasswordRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token, @NotBlank @Size(min = 12, max = 128) String password) {
    @Override public String toString() { return "ResetPasswordRequest[redacted]"; }
}
