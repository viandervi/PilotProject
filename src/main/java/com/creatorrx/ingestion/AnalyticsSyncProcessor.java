package com.creatorrx.ingestion;

import com.creatorrx.auth.CreatorGoogleApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.connection.annotation.Connectable;
import jakarta.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/** Resumable historical backfill with durable per-video range coverage and incremental refresh. */
@Singleton
@Requires(env = "database")
@Connectable
public class AnalyticsSyncProcessor implements SyncJobProcessor {
    private static final LocalDate EPOCH = LocalDate.of(1970, 1, 1);
    private static final int CHUNK_DAYS = 90;
    private static final int REFRESH_DAYS = 3;
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
        LocalDate end = LocalDate.now(ZoneOffset.UTC).minusDays(2);
        while (true) {
            int processed = 0;
            try (var connection = datasource.getConnection(); var stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, claim.channelId());
                stmt.setString(2, cursor);
                stmt.setString(3, cursor);
                try (var rows = stmt.executeQuery()) {
                    while (rows.next()) {
                        processed++;
                        UUID video = rows.getObject(1, UUID.class);
                        String youtubeId = rows.getString(2);
                        Timestamp published = rows.getTimestamp(3);
                        LocalDate publishedDate = published == null ? null :
                            published.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
                        LocalDate start = historicalStart(publishedDate, end);
                        int count = 0;
                        if (!start.isAfter(end)) {
                            // Stable epoch-aligned boundaries allow range coverage to be reused as the
                            // rolling 14-month window advances. Recheck the most recent 3 days.
                            LocalDate historicalEnd = end.minusDays(REFRESH_DAYS);
                            for (LocalDate from = start; !from.isAfter(historicalEnd); ) {
                                LocalDate to = chunkEnd(from, historicalEnd);
                                if (!isCovered(video, from, to)) {
                                    count += fetchAndStore(creator, video, youtubeId, from, to);
                                    markCovered(video, from, to);
                                }
                                from = to.plusDays(1);
                            }
                            LocalDate refreshStart = start.isAfter(historicalEnd.plusDays(1))
                                ? start : historicalEnd.plusDays(1);
                            if (!refreshStart.isAfter(end)) {
                                count += fetchAndStore(creator, video, youtubeId, refreshStart, end);
                                // Do not mark recent mutable days as immutable historical coverage.
                            }
                        }
                        if (!jobs.checkpoint(claim, youtubeId, count))
                            throw new IllegalStateException("Lost sync lease");
                        cursor = youtubeId;
                    }
                }
            }
            if (processed < 500) return;
        }
    }

    private int fetchAndStore(UUID creator, UUID video, String youtubeId,
                              LocalDate start, LocalDate end) throws Exception {
        JsonNode report = google.get(creator, "analytics", "/v2/reports", Map.of(
            "ids", "channel==MINE", "startDate", start.toString(), "endDate", end.toString(),
            "filters", "video==" + youtubeId, "dimensions", "day",
            "metrics", "views,estimatedMinutesWatched,averageViewDuration,averageViewPercentage,subscribersGained"));
        return metrics.saveDaily(video, report);
    }

    private boolean isCovered(UUID video, LocalDate start, LocalDate end) throws Exception {
        // PostgreSQL range_agg coalesces adjacent and overlapping fetched ranges.
        String sql = """
            SELECT COALESCE(range_agg(daterange(period_start, period_end + 1, '[)'))
                @> daterange(?::date, (?::date + 1), '[)'), false)
            FROM video_analytics_coverage WHERE video_id = ?
            """;
        try (var conn = datasource.getConnection(); var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, start); stmt.setObject(2, end); stmt.setObject(3, video);
            try (var rows = stmt.executeQuery()) {
                rows.next();
                return rows.getBoolean(1);
            }
        }
    }

    private void markCovered(UUID video, LocalDate start, LocalDate end) throws Exception {
        String sql = """
            INSERT INTO video_analytics_coverage(video_id, period_start, period_end)
            VALUES (?, ?, ?) ON CONFLICT DO NOTHING
            """;
        try (var conn = datasource.getConnection(); var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, video); stmt.setObject(2, start); stmt.setObject(3, end);
            stmt.executeUpdate();
        }
    }

    static LocalDate historicalStart(LocalDate published, LocalDate end) {
        LocalDate windowStart = end.plusDays(1).minusMonths(14);
        return published != null && published.isAfter(windowStart) ? published : windowStart;
    }

    static LocalDate chunkEnd(LocalDate from, LocalDate maximum) {
        long days = ChronoUnit.DAYS.between(EPOCH, from);
        long nextBoundary = (Math.floorDiv(days, CHUNK_DAYS) + 1L) * CHUNK_DAYS;
        LocalDate end = EPOCH.plusDays(nextBoundary - 1);
        return end.isAfter(maximum) ? maximum : end;
    }
}
