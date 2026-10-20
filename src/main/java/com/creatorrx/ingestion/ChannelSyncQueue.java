package com.creatorrx.ingestion;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.connection.annotation.Connectable;
import jakarta.inject.Singleton;
import javax.sql.DataSource;
import java.util.UUID;

/** Queues one active CHANNEL_SYNC per channel. */
@Singleton
@Requires(env = "database")
@Connectable
public class ChannelSyncQueue {
    private final DataSource datasource;
    public ChannelSyncQueue(DataSource datasource) { this.datasource = datasource; }

    public UUID enqueue(UUID creatorId, UUID channelId) throws Exception {
        String sql = """
            INSERT INTO sync_run(id, channel_id, sync_type)
            SELECT ?, c.id, 'CHANNEL_SYNC' FROM youtube_channel c
            WHERE c.id = ? AND c.creator_id = ?
            ON CONFLICT (channel_id, sync_type)
                WHERE status IN ('PENDING','RUNNING','RETRY_WAIT')
            DO UPDATE SET updated_at = sync_run.updated_at
            RETURNING id
            """;
        try (var connection = datasource.getConnection(); var stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, UUID.randomUUID()); stmt.setObject(2, channelId); stmt.setObject(3, creatorId);
            try (var rows = stmt.executeQuery()) {
                if (!rows.next()) throw new SecurityException("Channel not found for creator");
                return rows.getObject(1, UUID.class);
            }
        }
    }
}
