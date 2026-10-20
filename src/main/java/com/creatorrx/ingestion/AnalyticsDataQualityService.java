package com.creatorrx.ingestion;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.connection.annotation.Connectable;
import jakarta.inject.Singleton;
import javax.sql.DataSource;
import java.util.UUID;

/** Explicit coverage and sample counts: missing data is never interpreted as zero. */
@Singleton
@Requires(env = "database")
@Connectable
public class AnalyticsDataQualityService {
    private final DataSource datasource;
    public AnalyticsDataQualityService(DataSource datasource) { this.datasource = datasource; }

    public record Coverage(long videos, long videosWithDailyMetrics, long videosWithRetention,
                           long dailyMetricRows, long retentionPoints) {}

    public Coverage coverage(UUID creator, UUID channel) throws Exception {
        String sql = """
            SELECT COUNT(v.id) AS videos,
                COUNT(v.id) FILTER (WHERE EXISTS (SELECT 1 FROM video_daily_metric m WHERE m.video_id=v.id)) AS daily_videos,
                COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM video_retention_point r WHERE r.video_id=v.id)) AS retention_videos,
                COALESCE(SUM((SELECT COUNT(*) FROM video_daily_metric m WHERE m.video_id=v.id)),0) AS daily_rows,
                COALESCE(SUM((SELECT COUNT(*) FROM video_retention_point r WHERE r.video_id=v.id)),0) AS retention_rows
            FROM youtube_channel c
            LEFT JOIN video v ON v.channel_id=c.id
            WHERE c.id=? AND c.creator_id=?
            GROUP BY c.id
            """;
        try (var connection = datasource.getConnection(); var stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, channel); stmt.setObject(2, creator);
            try (var rows = stmt.executeQuery()) {
                if (!rows.next()) throw new SecurityException("Channel not owned by creator");
                return new Coverage(rows.getLong("videos"), rows.getLong("daily_videos"),
                    rows.getLong("retention_videos"), rows.getLong("daily_rows"), rows.getLong("retention_rows"));
            }
        }
    }
}
