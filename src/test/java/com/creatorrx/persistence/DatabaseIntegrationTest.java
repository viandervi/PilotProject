package com.creatorrx.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseIntegrationTest {
    @Test
    void migrationsAndUniqueConstraintsWork() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            var flyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .load();
            flyway.migrate();

            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                var creatorId = UUID.randomUUID();
                try (var insert = connection.prepareStatement(
                        "INSERT INTO creator(id, google_subject) VALUES (?, ?)")) {
                    insert.setObject(1, creatorId);
                    insert.setString(2, "google-subject-1");
                    assertEquals(1, insert.executeUpdate());
                }
                try (var insert = connection.prepareStatement(
                        "INSERT INTO youtube_channel(id, creator_id, youtube_channel_id, title) VALUES (?, ?, ?, ?)")) {
                    insert.setObject(1, UUID.randomUUID());
                    insert.setObject(2, creatorId);
                    insert.setString(3, "UC_test_channel");
                    insert.setString(4, "Test channel");
                    assertEquals(1, insert.executeUpdate());
                }
                try (var count = connection.prepareStatement("SELECT COUNT(*) FROM youtube_channel");
                     var rows = count.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(1, rows.getInt(1));
                }
                assertThrows(java.sql.SQLException.class, () -> {
                    try (var duplicate = connection.prepareStatement(
                            "INSERT INTO creator(id, google_subject) VALUES (?, ?)")) {
                        duplicate.setObject(1, UUID.randomUUID());
                        duplicate.setString(2, "google-subject-1");
                        duplicate.executeUpdate();
                    }
                });
            }
        }
    }
}
