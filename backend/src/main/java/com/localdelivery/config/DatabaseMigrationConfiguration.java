package com.localdelivery.config;

import java.sql.Connection;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseMigrationConfiguration {
    @Bean
    FlywayMigrationStrategy migrationStrategy() {
        return DatabaseMigrationConfiguration::migrate;
    }

    public static void migrate(Flyway flyway) {
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            String schema = connection.getSchema();
            boolean hasHistory = hasTable(connection, schema, "flyway_schema_history");
            if (!hasHistory && hasTable(connection, schema, "alembic_version")) {
                try (var statement = connection.createStatement();
                     var versions = statement.executeQuery("SELECT version_num FROM alembic_version")) {
                    if (!versions.next() || !"0001".equals(versions.getString(1)) || versions.next()) {
                        throw new IllegalStateException("Unsupported legacy database version.");
                    }
                }
                // Only the known Alembic schema is adopted; other nonempty schemas fail safely.
                flyway.baseline();
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Could not inspect database migration history.", error);
        }
        flyway.migrate();
    }

    private static boolean hasTable(Connection connection, String schema, String table) throws SQLException {
        try (var tables = connection.getMetaData().getTables(null, schema, table, new String[]{"TABLE"})) {
            return tables.next();
        }
    }
}
