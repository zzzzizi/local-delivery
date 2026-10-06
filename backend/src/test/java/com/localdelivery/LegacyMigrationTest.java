package com.localdelivery;

import com.localdelivery.config.DatabaseMigrationConfiguration;
import java.time.LocalDateTime;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.*;

class LegacyMigrationTest {
    @Test
    void adoptsLegacySchemaWithoutLosingRecordsOrInstants() {
        try (var database = new PostgresTestDatabase()) {
            var source = new DriverManagerDataSource(database.url(), database.username, database.password);
            var jdbc = new JdbcTemplate(source);
            Flyway.configure().dataSource(source).target("1").load().migrate();
            jdbc.execute("DROP TABLE flyway_schema_history");
            jdbc.execute("CREATE TABLE alembic_version (version_num VARCHAR(32) PRIMARY KEY)");
            jdbc.execute("INSERT INTO alembic_version VALUES ('0001')");
            jdbc.execute("""
                    INSERT INTO users(id, name, email, phone, rating, created_at) VALUES
                    (1, 'Customer Test', 'customer@example.com', NULL, NULL, '2026-10-05T18:00:00+02:00'),
                    (2, 'Helper Test', 'helper@example.com', '+4700000000', 4.50, '2026-10-05T18:00:00+02:00')
                    """);
            jdbc.execute("""
                    INSERT INTO delivery_requests(id, customer_id, helper_id, category, title, description,
                        pickup_address, delivery_address, shopping_budget, helper_reward, deadline, status, created_at, updated_at)
                    VALUES (7, 1, 2, 'BUY', 'Existing request', 'Milk and bread', 'Majorstuen', 'Frogner',
                        300.10, 80.25, '2026-10-06T18:00:00+02:00', 'ACCEPTED',
                        '2026-10-05T18:00:00+02:00', '2026-10-05T19:00:00+02:00')
                    """);
            var before = jdbc.queryForMap("SELECT id, customer_id, helper_id, title, description, category::text, status::text, shopping_budget, helper_reward FROM delivery_requests");
            var flyway = Flyway.configure().dataSource(source).baselineVersion("1").load();
            DatabaseMigrationConfiguration.migrate(flyway);
            DatabaseMigrationConfiguration.migrate(flyway);
            var after = jdbc.queryForMap("SELECT id, customer_id, helper_id, title, description, category::text, status::text, shopping_budget, helper_reward FROM delivery_requests");
            before.replaceAll((key, value) -> value instanceof Integer number ? number.longValue() : value);
            assertThat(after).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT created_at FROM users WHERE id=1", LocalDateTime.class))
                    .isEqualTo(LocalDateTime.parse("2026-10-05T16:00:00"));
            assertThat(jdbc.queryForObject("SELECT deadline FROM delivery_requests WHERE id=7", LocalDateTime.class))
                    .isEqualTo(LocalDateTime.parse("2026-10-06T16:00:00"));
            assertThat(jdbc.queryForObject("SELECT updated_at FROM delivery_requests WHERE id=7", LocalDateTime.class))
                    .isEqualTo(LocalDateTime.parse("2026-10-05T17:00:00"));
            assertThat(flyway.info().current().getVersion().toString()).isEqualTo("2");
            assertThat(jdbc.queryForObject("SELECT version_num FROM alembic_version", String.class)).isEqualTo("0001");
        }
    }

    @Test
    void refusesUnknownLegacyVersions() {
        try (var database = new PostgresTestDatabase()) {
            var source = new DriverManagerDataSource(database.url(), database.username, database.password);
            var jdbc = new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE alembic_version (version_num VARCHAR(32) PRIMARY KEY)");
            jdbc.execute("INSERT INTO alembic_version VALUES ('9999')");
            assertThatThrownBy(() -> DatabaseMigrationConfiguration.migrate(Flyway.configure().dataSource(source).load()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("Unsupported legacy");
            assertThat(jdbc.queryForObject("SELECT version_num FROM alembic_version", String.class)).isEqualTo("9999");
        }
    }
}
