package com.creatorrx.ingestion;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.connection.annotation.Connectable;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Atomic database-backed job claiming. Each statement uses its own short transaction.
 * No Google API calls occur while database locks are held.
 */
@Singleton
@Requires(env = "database")
@Connectable
public class SyncJobStore {
    private final DataSource dataSource;

    public SyncJobStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record Claim(UUID id, UUID channelId, String syncType, UUID leaseToken, int attempt) {}

    public Optional<Claim> claimNext(Duration leaseDuration) throws SQLException {
        if (leaseDuration.isNegative() || leaseDuration.isZero()) throw new IllegalArgumentException("leaseDuration");
        String sql = """
            WITH candidate AS (
                SELECT id FROM sync_run
                WHERE ((status IN ('PENDING', 'RETRY_WAIT') AND next_attempt_at <= now())
                   OR (status = 'RUNNING' AND lease_until < now()))
                  AND attempts < max_attempts
                ORDER BY created_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE sync_run s
            SET status = 'RUNNING', attempts = s.attempts + 1,
                lease_until = now() + (? * INTERVAL '1 second'),
                lease_token = ?, started_at = COALESCE(s.started_at, now()),
                updated_at = now(), error_code = NULL, error_message = NULL
            FROM candidate WHERE s.id = candidate.id
            RETURNING s.id, s.channel_id, s.sync_type, s.attempts
            """;
        var token = UUID.randomUUID();
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, leaseDuration.toSeconds());
            statement.setObject(2, token);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                return Optional.of(new Claim(
                        rows.getObject("id", UUID.class),
                        rows.getObject("channel_id", UUID.class),
                        rows.getString("sync_type"), token, rows.getInt("attempts")));
            }
        }
    }

    public String cursor(Claim claim) throws SQLException {
        try (var connection = dataSource.getConnection();
             var stmt = connection.prepareStatement(
                "SELECT page_cursor FROM sync_run WHERE id = ? AND lease_token = ? AND status = 'RUNNING' AND lease_until > now()")) {
            stmt.setObject(1, claim.id()); stmt.setObject(2, claim.leaseToken());
            try (var rows = stmt.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Lost sync lease");
                return rows.getString(1);
            }
        }
    }

    public boolean checkpoint(Claim claim, String nextPage, int items) throws SQLException {
        if (items < 0) throw new IllegalArgumentException("items");
        String sql = """
            UPDATE sync_run SET page_cursor = ?, progress_items = progress_items + ?,
                lease_until = now() + INTERVAL '10 minutes', updated_at = now()
            WHERE id = ? AND lease_token = ? AND status = 'RUNNING' AND lease_until > now()
            """;
        try (var connection = dataSource.getConnection(); var stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, nextPage); stmt.setInt(2, items);
            stmt.setObject(3, claim.id()); stmt.setObject(4, claim.leaseToken());
            return stmt.executeUpdate() == 1;
        }
    }

    public boolean complete(Claim claim) throws SQLException {
        return transition(claim, "COMPLETED", null, null, null);
    }

    public boolean fail(Claim claim, boolean retryable, Duration delay, String code, String message) throws SQLException {
        if (retryable && (delay == null || delay.isNegative())) throw new IllegalArgumentException("delay");
        String sql = """
            UPDATE sync_run
            SET status = CASE WHEN ? AND attempts < max_attempts THEN 'RETRY_WAIT' ELSE 'FAILED' END,
                next_attempt_at = now() + (? * INTERVAL '1 second'),
                lease_until = NULL, lease_token = NULL,
                error_code = ?, error_message = ?, updated_at = now()
            WHERE id = ? AND lease_token = ? AND status = 'RUNNING' AND lease_until > now()
            """;
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, retryable);
            statement.setLong(2, retryable ? delay.toSeconds() : 0);
            statement.setString(3, code);
            statement.setString(4, message);
            statement.setObject(5, claim.id());
            statement.setObject(6, claim.leaseToken());
            return statement.executeUpdate() == 1;
        }
    }

    public boolean renew(Claim claim, Duration duration) throws SQLException {
        if (duration.isNegative() || duration.isZero()) throw new IllegalArgumentException("duration");
        String sql = """
            UPDATE sync_run SET lease_until = now() + (? * INTERVAL '1 second'), updated_at = now()
            WHERE id = ? AND lease_token = ? AND status = 'RUNNING' AND lease_until > now()
            """;
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, duration.toSeconds());
            statement.setObject(2, claim.id());
            statement.setObject(3, claim.leaseToken());
            return statement.executeUpdate() == 1;
        }
    }

    private boolean transition(Claim claim, String status, String code, String message, Instant nextAttempt) throws SQLException {
        String sql = """
            UPDATE sync_run SET status = ?, completed_at = now(),
                lease_until = NULL, lease_token = NULL, updated_at = now()
            WHERE id = ? AND lease_token = ? AND status = 'RUNNING' AND lease_until > now()
            """;
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setObject(2, claim.id());
            statement.setObject(3, claim.leaseToken());
            return statement.executeUpdate() == 1;
        }
    }
}
