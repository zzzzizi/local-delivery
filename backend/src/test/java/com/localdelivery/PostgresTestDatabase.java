package com.localdelivery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;

final class PostgresTestDatabase implements AutoCloseable {
    final String schema = "test_java_" + UUID.randomUUID().toString().replace("-", "");
    final String baseUrl;
    final String username;
    final String password;

    PostgresTestDatabase() {
        Properties local = new Properties();
        try {
            Path file = Path.of("../.env");
            if (Files.exists(file)) {
                try (var reader = Files.newBufferedReader(file)) { local.load(reader); }
            }
            baseUrl = "jdbc:postgresql://" + setting(local, "DB_HOST", "POSTGRES_HOST", "127.0.0.1")
                    + ":" + setting(local, "DB_PORT", "POSTGRES_PORT", "5433")
                    + "/" + setting(local, "DB_NAME", "POSTGRES_DB", "local_delivery");
            username = setting(local, "DB_USERNAME", "POSTGRES_USER", "local_delivery");
            password = setting(local, "DB_PASSWORD", "POSTGRES_PASSWORD", "");
            if (password.isBlank()) throw new IllegalStateException("Set DB_PASSWORD in .env before testing.");
            execute("CREATE SCHEMA " + schema);
        } catch (IOException error) {
            throw new IllegalStateException("Could not read local database test settings.", error);
        }
    }

    String url() { return baseUrl + "?currentSchema=" + schema; }

    private static String setting(Properties local, String key, String legacy, String fallback) {
        String value = System.getenv(key);
        if (value == null) value = System.getenv(legacy);
        if (value == null) value = local.getProperty(key, local.getProperty(legacy, fallback));
        return value;
    }

    private void execute(String sql) {
        try (var connection = DriverManager.getConnection(baseUrl, username, password);
             var statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException error) {
            throw new IllegalStateException("PostgreSQL test schema operation failed. Is the database running?", error);
        }
    }

    @Override
    public void close() {
        // The identifier is generated here, never supplied by an external caller.
        execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }
}
