package com.localdelivery.controller;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReadinessController {
    private final JdbcTemplate jdbc;
    public ReadinessController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @GetMapping("/api/health/ready")
    public ResponseEntity<Map<String, String>> ready() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (DataAccessException error) {
            return ResponseEntity.status(503).body(Map.of("status", "unavailable"));
        }
    }
}
