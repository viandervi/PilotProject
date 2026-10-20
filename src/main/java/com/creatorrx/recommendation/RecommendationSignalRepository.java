package com.creatorrx.recommendation;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import javax.sql.DataSource;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reads comparable 28-day and preceding 28-day windows. Baselines use peer videos
 * in the same channel and age cohort; insufficient peers produce null, never zero.
 */
@Singleton
@Requires(env = "database")
public class RecommendationSignalRepository {
    private final DataSource datasource;
    public RecommendationSignalRepository(DataSource datasource) { this.datasource = datasource; }

    public List<VideoSignal> load(UUID creator, UUID channel, LocalDate asOf) throws Exception {
        String sql = """
            WITH retention AS (
                SELECT video_id, AVG(audience_watch_ratio)::double precision AS early_retention
                FROM video_retention_point
                WHERE period_end <= ?::date AND period_end >= ?::date - 90
                  AND elapsed_ratio BETWEEN 0.10 AND 0.30
                GROUP BY video_id
            ), base AS (
                SELECT v.id, v.published_at, v.youtube_video_id, r.early_retention,
                       COALESCE(SUM(m.views) FILTER (WHERE m.metric_date BETWEEN ?::date - 27 AND ?::date), 0) AS recent_views,
                       SUM(m.views) FILTER (WHERE m.metric_date BETWEEN ?::date - 55 AND ?::date - 28) AS previous_views,
                       COUNT(DISTINCT m.metric_date) FILTER (WHERE m.metric_date BETWEEN ?::date - 27 AND ?::date) AS observed_days,
                       SUM(m.average_view_percentage * m.views) FILTER (WHERE m.metric_date BETWEEN ?::date - 27 AND ?::date)
                          / NULLIF(SUM(m.views) FILTER (WHERE m.metric_date BETWEEN ?::date - 27 AND ?::date), 0) AS avg_percentage,
                       CASE WHEN v.published_at IS NULL THEN NULL
                            WHEN CAST(v.published_at AS date) > ?::date - 90 THEN 'NEW'
                            WHEN CAST(v.published_at AS date) > ?::date - 365 THEN 'ESTABLISHED'
                            ELSE 'EVERGREEN' END AS cohort
                FROM video v
                LEFT JOIN video_daily_metric m ON m.video_id = v.id
                LEFT JOIN retention r ON r.video_id = v.id
                WHERE v.channel_id = ?
                GROUP BY v.id, r.early_retention
            ), peers AS (
                SELECT b.*, (SUM(b.avg_percentage) OVER (PARTITION BY b.cohort) - COALESCE(b.avg_percentage,0)) /
                    NULLIF(COUNT(b.avg_percentage) OVER (PARTITION BY b.cohort) - CASE WHEN b.avg_percentage IS NULL THEN 0 ELSE 1 END, 0) AS peer_avg,
                    COUNT(b.avg_percentage) OVER (PARTITION BY b.cohort) AS peer_count,
                    (SUM(b.early_retention) OVER (PARTITION BY b.cohort) - COALESCE(b.early_retention,0)) /
                    NULLIF(COUNT(b.early_retention) OVER (PARTITION BY b.cohort) - CASE WHEN b.early_retention IS NULL THEN 0 ELSE 1 END, 0) AS peer_early,
                    COUNT(b.early_retention) OVER (PARTITION BY b.cohort) AS early_peer_count
                FROM base b
            )
            SELECT id, published_at, recent_views, previous_views, observed_days,
                   avg_percentage, CASE WHEN peer_count >= 6 THEN peer_avg ELSE NULL END AS cohort_avg,
                   early_retention, CASE WHEN early_peer_count >= 6 THEN peer_early ELSE NULL END AS cohort_early
            FROM peers ORDER BY id
            """;
        var result = new ArrayList<VideoSignal>();
        try (var connection = datasource.getConnection()) {
            try (var ownership = connection.prepareStatement(
                    "SELECT 1 FROM youtube_channel WHERE id=? AND creator_id=?")) {
                ownership.setObject(1, channel); ownership.setObject(2, creator);
                try (var rows = ownership.executeQuery()) {
                    if (!rows.next()) throw new SecurityException("Channel not owned by creator");
                }
            }
            try (var stmt = connection.prepareStatement(sql)) {
                for (int i=1; i<=14; i++) stmt.setObject(i, asOf);
                stmt.setObject(15, channel);
                try (var rows = stmt.executeQuery()) {
                    while (rows.next()) {
                        var published = rows.getTimestamp("published_at");
                        result.add(new VideoSignal(
                            rows.getObject("id", UUID.class),
                            published == null ? null : published.toInstant().atZone(ZoneOffset.UTC).toLocalDate(),
                            rows.getLong("recent_views"), nullableLong(rows, "previous_views"),
                            nullableDouble(rows, "avg_percentage"), nullableDouble(rows, "cohort_avg"),
                            nullableDouble(rows, "early_retention"), nullableDouble(rows, "cohort_early"), rows.getInt("observed_days")));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static Long nullableLong(ResultSet rows, String name) throws Exception {
        long value = rows.getLong(name);
        return rows.wasNull() ? null : value;
    }

    private static Double nullableDouble(ResultSet rows, String name) throws Exception {
        double value = rows.getDouble(name);
        return rows.wasNull() ? null : value;
    }
}
