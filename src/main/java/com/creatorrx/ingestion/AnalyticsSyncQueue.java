package com.creatorrx.ingestion;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import javax.sql.DataSource;
import java.util.UUID;

@Singleton
@Requires(env = "database")
public class AnalyticsSyncQueue {
    private final DataSource datasource;
    public AnalyticsSyncQueue(DataSource datasource) { this.datasource = datasource; }
    public UUID enqueue(UUID creator, UUID channel) throws Exception {
        String sql = """
            INSERT INTO sync_run(id, channel_id, sync_type)
            SELECT ?, id, 'ANALYTICS_SYNC' FROM youtube_channel WHERE id = ? AND creator_id = ?
            ON CONFLICT (channel_id, sync_type)
                WHERE status IN ('PENDING','RUNNING','RETRY_WAIT')
            DO UPDATE SET updated_at = sync_run.updated_at
            RETURNING id
            """;
        try (var connection = datasource.getConnection(); var stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, UUID.randomUUID()); stmt.setObject(2, channel); stmt.setObject(3, creator);
            try (var rows = stmt.executeQuery()) {
                if (!rows.next()) throw new SecurityException("Channel not owned by creator");
                return rows.getObject(1, UUID.class);
            }
        }
    }
}
