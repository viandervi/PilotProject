package com.creatorrx.ingestion;

import com.creatorrx.auth.CreatorGoogleApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.connection.annotation.Connectable;
import jakarta.inject.Singleton;
import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/** Bounded per-video Analytics backfill with lease-guarded progress. */
@Singleton
@Requires(env = "database")
@Connectable
public class AnalyticsSyncProcessor implements SyncJobProcessor {
    private final DataSource datasource;
    private final CreatorGoogleApiClient google;
    private final AnalyticsMetricStore metrics;
    private final SyncJobStore jobs;

    public AnalyticsSyncProcessor(DataSource datasource, CreatorGoogleApiClient google,
                                  AnalyticsMetricStore metrics, SyncJobStore jobs) {
        this.datasource = datasource; this.google = google; this.metrics = metrics; this.jobs = jobs;
    }

    @Override public String syncType() { return "ANALYTICS_SYNC"; }
    @Override public void process(UUID channelId) { throw new UnsupportedOperationException("Lease-bound claim required"); }

    public void process(SyncJobStore.Claim claim) throws Exception {
        UUID creator;
        try (var connection = datasource.getConnection();
             var stmt = connection.prepareStatement("SELECT creator_id FROM youtube_channel WHERE id = ?")) {
            stmt.setObject(1, claim.channelId());
            try (var rows = stmt.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Channel missing");
                creator = rows.getObject(1, UUID.class);
            }
        }
        String cursor = jobs.cursor(claim);
        String sql = """
            SELECT id, youtube_video_id, published_at FROM video
            WHERE channel_id = ? AND (? IS NULL OR youtube_video_id > ?)
            ORDER BY youtube_video_id LIMIT 500
            """;
        while (true) {
        int processed = 0;
        try (var connection = datasource.getConnection(); var stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, claim.channelId()); stmt.setString(2, cursor); stmt.setString(3, cursor);
            try (var rows = stmt.executeQuery()) {
                while (rows.next()) {
                    processed++;
                    UUID video = rows.getObject(1, UUID.class);
                    String youtubeId = rows.getString(2);
                    var published = rows.getTimestamp(3);
                    LocalDate end = LocalDate.now(ZoneOffset.UTC).minusDays(2);
                    LocalDate start = historicalStart(published == null ? null :
                        published.toInstant().atZone(ZoneOffset.UTC).toLocalDate(), end);
                    if (!start.isAfter(end)) {
                        int count = 0;
                        // Keep requests bounded; each chunk is upserted before advancing the video cursor.
                        for (LocalDate chunkStart = start; !chunkStart.isAfter(end); chunkStart = chunkStart.plusDays(90)) {
                            LocalDate chunkEnd = chunkStart.plusDays(89);
                            if (chunkEnd.isAfter(end)) chunkEnd = end;
                            JsonNode report = google.get(creator, "analytics", "/v2/reports", Map.of(
                                "ids", "channel==MINE", "startDate", chunkStart.toString(), "endDate", chunkEnd.toString(),
                                "filters", "video==" + youtubeId, "dimensions", "day",
                                "metrics", "views,estimatedMinutesWatched,averageViewDuration,averageViewPercentage,subscribersGained"));
                            count += metrics.saveDaily(video, report);
                        }
                        if (!jobs.checkpoint(claim, youtubeId, count)) throw new IllegalStateException("Lost sync lease");
                        cursor = youtubeId;
                    } else {
                        if (!jobs.checkpoint(claim, youtubeId, 0)) throw new IllegalStateException("Lost sync lease");
                        cursor = youtubeId;
                    }
                }
            }
        }
        if (processed < 500) break;
        }
    }

    /** Default initial backfill: fourteen calendar months, bounded by publication date. */
    static LocalDate historicalStart(LocalDate published, LocalDate end) {
        LocalDate windowStart = end.plusDays(1).minusMonths(14);
        return published != null && published.isAfter(windowStart) ? published : windowStart;
    }
}
