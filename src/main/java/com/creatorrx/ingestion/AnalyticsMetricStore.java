package com.creatorrx.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Validated idempotent YouTube Analytics daily and retention upserts. */
@Singleton
@Requires(env = "database")
public class AnalyticsMetricStore {
    private final DataSource dataSource;
    public AnalyticsMetricStore(DataSource dataSource) { this.dataSource = dataSource; }

    public int saveDaily(UUID video, JsonNode report) throws Exception {
        int saved = 0;
        for (JsonNode row : report.path("rows")) {
            if (!row.isArray() || row.size() != 6) throw new IllegalArgumentException("Unexpected daily Analytics schema");
            LocalDate date = LocalDate.parse(row.get(0).asText());
            long views = row.get(1).asLong(-1);
            BigDecimal minutes = decimal(row.get(2)), duration = decimal(row.get(3));
            BigDecimal percentage = nullableDecimal(row.get(4));
            long subscribers = row.get(5).asLong(-1);
            if (views < 0 || subscribers < 0 || minutes.signum() < 0 || duration.signum() < 0)
                throw new IllegalArgumentException("Invalid negative Analytics metric");
            String sql = """
                INSERT INTO video_daily_metric(video_id, metric_date, views, watch_minutes,
                    average_view_duration, average_view_percentage, subscribers_gained)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (video_id, metric_date) DO UPDATE SET
                    views=EXCLUDED.views, watch_minutes=EXCLUDED.watch_minutes,
                    average_view_duration=EXCLUDED.average_view_duration,
                    average_view_percentage=EXCLUDED.average_view_percentage,
                    subscribers_gained=EXCLUDED.subscribers_gained, updated_at=now()
                """;
            try (var connection = dataSource.getConnection(); var stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, video); stmt.setObject(2, date); stmt.setLong(3, views);
                stmt.setBigDecimal(4, minutes); stmt.setBigDecimal(5, duration);
                stmt.setBigDecimal(6, percentage); stmt.setLong(7, subscribers);
                stmt.executeUpdate();
            }
            saved++;
        }
        return saved;
    }

    public int saveRetention(UUID video, LocalDate start, LocalDate end, JsonNode report) throws Exception {
        int saved = 0;
        for (JsonNode row : report.path("rows")) {
            if (!row.isArray() || row.size() != 3) throw new IllegalArgumentException("Unexpected retention schema");
            BigDecimal elapsed = decimal(row.get(0)), audience = decimal(row.get(1)), relative = nullableDecimal(row.get(2));
            if (elapsed.signum() < 0 || elapsed.compareTo(BigDecimal.ONE) > 0 || audience.signum() < 0)
                throw new IllegalArgumentException("Invalid retention metric");
            String sql = """
                INSERT INTO video_retention_point(video_id, period_start, period_end, elapsed_ratio,
                    audience_watch_ratio, relative_retention_performance)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (video_id, period_start, period_end, elapsed_ratio) DO UPDATE SET
                    audience_watch_ratio=EXCLUDED.audience_watch_ratio,
                    relative_retention_performance=EXCLUDED.relative_retention_performance, updated_at=now()
                """;
            try (var connection = dataSource.getConnection(); var stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, video); stmt.setObject(2, start); stmt.setObject(3, end);
                stmt.setBigDecimal(4, elapsed); stmt.setBigDecimal(5, audience); stmt.setBigDecimal(6, relative);
                stmt.executeUpdate();
            }
            saved++;
        }
        return saved;
    }

    private static BigDecimal decimal(JsonNode value) {
        if (value == null || value.isNull() || !value.isNumber()) throw new IllegalArgumentException("Missing numeric metric");
        return value.decimalValue();
    }
    private static BigDecimal nullableDecimal(JsonNode value) {
        return value == null || value.isNull() ? null : decimal(value);
    }
}
