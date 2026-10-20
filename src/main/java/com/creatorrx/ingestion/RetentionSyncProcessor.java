package com.creatorrx.ingestion;

import com.creatorrx.auth.CreatorGoogleApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/** Resumable per-video retention snapshots for a bounded trailing window. */
@Singleton
@Requires(env = "database")
public class RetentionSyncProcessor implements SyncJobProcessor {
    private final DataSource datasource;
    private final CreatorGoogleApiClient google;
    private final AnalyticsMetricStore metrics;
    private final SyncJobStore jobs;

    public RetentionSyncProcessor(DataSource datasource, CreatorGoogleApiClient google,
                                  AnalyticsMetricStore metrics, SyncJobStore jobs) {
        this.datasource = datasource; this.google = google; this.metrics = metrics; this.jobs = jobs;
    }

    @Override public String syncType() { return "RETENTION_SYNC"; }
    @Override public void process(UUID channelId) { throw new UnsupportedOperationException("Lease-bound claim required"); }

    public void process(SyncJobStore.Claim claim) throws Exception {
        UUID creator;
        try (var connection = datasource.getConnection();
             var stmt = connection.prepareStatement("SELECT creator_id FROM youtube_channel WHERE id = ?")) {
            stmt.setObject(1, claim.channelId());
            try (var rows = stmt.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Channel not found");
                creator = rows.getObject(1, UUID.class);
            }
        }
        String cursor = jobs.cursor(claim);
        String sql = """
            SELECT id, youtube_video_id, published_at FROM video
            WHERE channel_id = ? AND (? IS NULL OR youtube_video_id > ?)
            ORDER BY youtube_video_id LIMIT 100
            """;
        while (true) {
            int processed = 0;
            try (var connection = datasource.getConnection(); var stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, claim.channelId());
                stmt.setString(2, cursor); stmt.setString(3, cursor);
                try (var rows = stmt.executeQuery()) {
                    while (rows.next()) {
                        processed++;
                        UUID video = rows.getObject(1, UUID.class);
                        String youtubeId = rows.getString(2);
                        var published = rows.getTimestamp(3);
                        LocalDate end = LocalDate.now(ZoneOffset.UTC).minusDays(2);
                        LocalDate start = end.minusDays(89);
                        if (published != null) {
                            LocalDate publishedDate = published.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
                            if (publishedDate.isAfter(start)) start = publishedDate;
                        }
                        int count = 0;
                        if (!start.isAfter(end)) {
                            JsonNode report = google.get(creator, "analytics", "/v2/reports", Map.of(
                                "ids", "channel==MINE", "startDate", start.toString(), "endDate", end.toString(),
                                "filters", "video==" + youtubeId,
                                "dimensions", "elapsedVideoTimeRatio",
                                "metrics", "audienceWatchRatio,relativeRetentionPerformance",
                                "sort", "elapsedVideoTimeRatio"));
                            count = metrics.saveRetention(video, start, end, report);
                        }
                        if (!jobs.checkpoint(claim, youtubeId, count)) throw new IllegalStateException("Lost retention sync lease");
                        cursor = youtubeId;
                    }
                }
            }
            if (processed < 100) return;
        }
    }
}
