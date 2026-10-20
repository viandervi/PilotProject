package com.creatorrx.ingestion;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import javax.sql.DataSource;
import java.util.UUID;

/** Idempotent channel/video writes, guarded against cross-creator reassignment. */
@Singleton
@Requires(env = "database")
public class ChannelCatalogStore {
    private final DataSource datasource;
    public ChannelCatalogStore(DataSource datasource) { this.datasource = datasource; }

    public UUID upsertChannel(UUID creatorId, String youtubeId, String title, String uploads) throws Exception {
        String sql = """
            INSERT INTO youtube_channel(id, creator_id, youtube_channel_id, title, uploads_playlist_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (youtube_channel_id) DO UPDATE SET
                title = EXCLUDED.title, uploads_playlist_id = EXCLUDED.uploads_playlist_id, updated_at = now()
            WHERE youtube_channel.creator_id = EXCLUDED.creator_id
            RETURNING id
            """;
        try (var connection = datasource.getConnection(); var stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, UUID.randomUUID()); stmt.setObject(2, creatorId);
            stmt.setString(3, youtubeId); stmt.setString(4, title); stmt.setString(5, uploads);
            try (var rows = stmt.executeQuery()) {
                if (!rows.next()) throw new SecurityException("YouTube channel belongs to another creator");
                return rows.getObject(1, UUID.class);
            }
        }
    }

    public void upsertVideo(UUID channelId, String videoId, String title, java.time.Instant publishedAt) throws Exception {
        if (!videoId.matches("[A-Za-z0-9_-]{11}")) throw new IllegalArgumentException("Invalid YouTube video ID");
        String sql = """
            INSERT INTO video(id, channel_id, youtube_video_id, title, published_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (youtube_video_id) DO UPDATE SET title = EXCLUDED.title,
                published_at = EXCLUDED.published_at, updated_at = now()
            WHERE video.channel_id = EXCLUDED.channel_id
            RETURNING id
            """;
        try (var connection = datasource.getConnection(); var stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, UUID.randomUUID()); stmt.setObject(2, channelId);
            stmt.setString(3, videoId); stmt.setString(4, title);
            if (publishedAt == null) stmt.setNull(5, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
            else stmt.setObject(5, java.time.OffsetDateTime.ofInstant(publishedAt, java.time.ZoneOffset.UTC));
            try (var rows = stmt.executeQuery()) {
                if (!rows.next()) throw new SecurityException("Video belongs to another channel");
            }
        }
    }
}
