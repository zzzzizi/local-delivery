package com.localdelivery.dto;

import java.util.Locale;

final class Emails {
    private Emails() {}

    /** Login, registration, and recovery must agree on one canonical form. */
    static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
