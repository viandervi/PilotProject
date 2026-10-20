package com.creatorrx.ingestion;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import org.postgresql.ds.PGSimpleDataSource;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SyncJobStoreIntegrationTest {
    @Test
    void claimsAreExclusiveAndExpiredLeasesCanBeReclaimed() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .load().migrate();
            var dataSource = new PGSimpleDataSource();
            dataSource.setURL(postgres.getJdbcUrl());
            dataSource.setUser(postgres.getUsername());
            dataSource.setPassword(postgres.getPassword());
            var store = new SyncJobStore(dataSource);
            var channelId = UUID.randomUUID();
            var jobId = UUID.randomUUID();
            try (var conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                try (var stmt = conn.prepareStatement("INSERT INTO creator(id, google_subject) VALUES (?, ?)")) {
                    stmt.setObject(1, UUID.randomUUID());
                    stmt.setString(2, "sub-1");
                    stmt.executeUpdate();
                }
                UUID creatorId;
                try (var stmt = conn.prepareStatement("SELECT id FROM creator WHERE google_subject='sub-1'");
                     var rows = stmt.executeQuery()) {
                    assertTrue(rows.next());
                    creatorId = rows.getObject(1, UUID.class);
                }
                try (var stmt = conn.prepareStatement(
                        "INSERT INTO youtube_channel(id, creator_id, youtube_channel_id, title) VALUES (?, ?, 'UC_test', 'test')")) {
                    stmt.setObject(1, channelId);
                    stmt.setObject(2, creatorId);
                    stmt.executeUpdate();
                }
                try (var stmt = conn.prepareStatement(
                        "INSERT INTO sync_run(id, channel_id, sync_type) VALUES (?, ?, 'CHANNEL_SYNC')")) {
                    stmt.setObject(1, jobId);
                    stmt.setObject(2, channelId);
                    stmt.executeUpdate();
                }
            }
            var first = store.claimNext(Duration.ofMinutes(5)).orElseThrow();
            assertEquals(jobId, first.id());
            assertTrue(store.claimNext(Duration.ofMinutes(5)).isEmpty());
            assertTrue(store.renew(first, Duration.ofMinutes(5)));
            assertTrue(store.fail(first, true, Duration.ZERO, "TRANSIENT", "retry"));
            var second = store.claimNext(Duration.ofMinutes(5)).orElseThrow();
            assertNotEquals(first.leaseToken(), second.leaseToken());
            assertFalse(store.complete(first));
            assertTrue(store.complete(second));
            assertTrue(store.claimNext(Duration.ofMinutes(5)).isEmpty());
        }
    }
}
