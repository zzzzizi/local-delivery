package com.localdelivery.service;

import com.localdelivery.exception.InvalidAccountTokenException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountTokenService {
    public enum Purpose { VERIFY_EMAIL, RESET_PASSWORD }
    public record MailToken(String email, String token, Purpose purpose) {
        @Override public String toString() { return "MailToken[redacted]"; }
    }
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final PasswordEncoder passwords;
    private final SecureRandom random = new SecureRandom();
    public AccountTokenService(JdbcTemplate jdbc, Clock clock, PasswordEncoder passwords) {
        this.jdbc = jdbc; this.clock = clock; this.passwords = passwords;
    }
    private OffsetDateTime now() { return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }

    @Transactional
    public Optional<MailToken> issue(String email, Purpose purpose) {
        var rows = jdbc.queryForList("SELECT id, email, email_verified, password_hash FROM users WHERE lower(email) = lower(?) FOR UPDATE", email);
        if (rows.isEmpty()) return Optional.empty();
        var user = rows.getFirst();
        // Preserved demo accounts have no credentials and cannot be claimed by recovery.
        if (user.get("password_hash") == null) return Optional.empty();
        boolean verified = (boolean) user.get("email_verified");
        if (purpose == Purpose.VERIFY_EMAIL && verified || purpose == Purpose.RESET_PASSWORD && !verified) return Optional.empty();
        var bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var lifetime = purpose == Purpose.VERIFY_EMAIL ? Duration.ofHours(24) : Duration.ofMinutes(30);
        jdbc.update("DELETE FROM account_tokens WHERE user_id = ? AND purpose = ?", user.get("id"), purpose.name());
        jdbc.update("INSERT INTO account_tokens(token_hash, user_id, purpose, expires_at) VALUES (?, ?, ?, ?)",
                hash(token), user.get("id"), purpose.name(), now().plus(lifetime));
        return Optional.of(new MailToken((String) user.get("email"), token, purpose));
    }

    @Transactional
    public void verify(String token) {
        long id = consume(token, Purpose.VERIFY_EMAIL);
        jdbc.update("UPDATE users SET email_verified = TRUE WHERE id = ?", id);
    }

    @Transactional
    public String reset(String token, String password) {
        long id = consume(token, Purpose.RESET_PASSWORD);
        String email = jdbc.queryForObject(
                "UPDATE users SET password_hash = ?, session_version = session_version + 1 WHERE id = ? RETURNING email",
                String.class, passwords.encode(password), id);
        jdbc.update("DELETE FROM account_tokens WHERE user_id = ?", id);
        return email;
    }

    private long consume(String token, Purpose purpose) {
        String hash = hash(token);
        var users = jdbc.queryForList("SELECT user_id FROM account_tokens WHERE token_hash = ? AND purpose = ?", Long.class, hash, purpose.name());
        if (users.isEmpty()) throw new InvalidAccountTokenException();
        long id = users.getFirst();
        // Same lock order as issue(): serialize issuance/reset/verification for this account.
        jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, id);
        int consumed = jdbc.update("DELETE FROM account_tokens WHERE token_hash = ? AND purpose = ? AND expires_at > ?", hash, purpose.name(), now());
        if (consumed != 1) throw new InvalidAccountTokenException();
        return id;
    }
    private static String hash(String token) { return Sha256.hex(token); }
}
