package com.localdelivery.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoUserService {
    private final JdbcTemplate jdbc;

    public DemoUserService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void seed() {
        addUser(1, "Customer Test", "customer@example.com");
        addUser(2, "Helper Test", "helper@example.com");
        jdbc.queryForObject("""
                SELECT setval(pg_get_serial_sequence('users', 'id'),
                    GREATEST((SELECT MAX(id) FROM users), (SELECT last_value FROM users_id_seq)), true)
                """, Long.class);
    }

    private void addUser(long id, String name, String email) {
        var existing = jdbc.queryForList("SELECT email FROM users WHERE id = ?", String.class, id);
        if (!existing.isEmpty()) {
            if (!email.equals(existing.getFirst())) {
                throw new IllegalStateException("Demo user ID " + id + " is already in use.");
            }
            return;
        }
        if (!jdbc.queryForList("SELECT id FROM users WHERE email = ?", Long.class, email).isEmpty()) {
            throw new IllegalStateException("Demo email for user " + id + " is already in use.");
        }
        jdbc.update("INSERT INTO users (id, name, email) VALUES (?, ?, ?)", id, name, email);
    }
}
