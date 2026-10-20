package com.creatorrx.recommendation;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.connection.annotation.Connectable;
import jakarta.inject.Singleton;
import javax.sql.DataSource;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Evidence-first diagnostics. Observations are not causal explanations.
 * YouTube reach/traffic-source metrics are not ingested; never fabricate them.
 */
@Singleton
@Requires(env = "database")
@Connectable
public class PerformanceDiagnosisService {
    private final DataSource datasource;
    public PerformanceDiagnosisService(DataSource datasource) { this.datasource = datasource; }

    public record Evidence(String metric, Double observed, Double baseline, String window, String interpretation) {}
    public record Diagnosis(UUID videoId, String format, String status, String observation,
                            String hypothesis, double confidence, List<Evidence> evidence,
                            List<String> missingEvidence) {}

    public List<Diagnosis> forChannel(UUID creator, UUID channel) throws Exception {
        String sql = """
            WITH totals AS (
              SELECT v.id, v.duration_seconds,
                COUNT(m.metric_date) FILTER (WHERE m.metric_date >= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 29 AND m.metric_date < (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 1) AS days_recent,
                COALESCE(SUM(m.views) FILTER (WHERE m.metric_date >= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 29 AND m.metric_date < (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 1),0) AS recent_views,
                SUM(m.views) FILTER (WHERE m.metric_date >= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 57 AND m.metric_date < (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 29) AS previous_views,
                COALESCE(SUM(m.views),0) AS historical_views,
                MIN(m.metric_date) AS first_metric,
                MAX(m.metric_date) AS last_metric,
                SUM(m.average_view_percentage * m.views) FILTER (WHERE m.metric_date >= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 29 AND m.metric_date < (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 1)
                  / NULLIF(SUM(m.views) FILTER (WHERE m.metric_date >= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 29 AND m.metric_date < (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 1),0) AS recent_retention,
                SUM(m.average_view_percentage * m.views) / NULLIF(SUM(m.views),0) AS historical_retention,
                CASE WHEN v.duration_seconds IS NULL THEN 'UNKNOWN'
                     WHEN v.duration_seconds <= 180 THEN 'SHORT_OR_BRIEF'
                     ELSE 'LONG_FORM' END AS format
              FROM video v LEFT JOIN video_daily_metric m ON m.video_id=v.id
              WHERE v.channel_id=?
              GROUP BY v.id
            ), peers AS (
              SELECT t.*,
                (SUM(recent_retention) OVER (PARTITION BY format) - COALESCE(recent_retention,0))
                  / NULLIF(COUNT(recent_retention) OVER (PARTITION BY format)
                    - CASE WHEN recent_retention IS NULL THEN 0 ELSE 1 END,0) AS peer_retention,
                COUNT(recent_retention) OVER (PARTITION BY format)
                    - CASE WHEN recent_retention IS NULL THEN 0 ELSE 1 END AS peer_count
              FROM totals t
            )
            SELECT * FROM peers ORDER BY id
            """;
        try (var conn=datasource.getConnection()) {
            try (var own=conn.prepareStatement("SELECT 1 FROM youtube_channel WHERE id=? AND creator_id=?")) {
                own.setObject(1,channel); own.setObject(2,creator);
                try(var rows=own.executeQuery()) { if(!rows.next()) throw new SecurityException("Channel not owned by creator"); }
            }
            var output=new ArrayList<Diagnosis>();
            try(var stmt=conn.prepareStatement(sql)) {
                stmt.setObject(1,channel);
                try(var rows=stmt.executeQuery()) {
                    while(rows.next()) output.add(diagnose(rows));
                }
            }
            return List.copyOf(output);
        }
    }

    private static Diagnosis diagnose(ResultSet r) throws Exception {
        UUID id=r.getObject("id",UUID.class);
        String format=r.getString("format");
        int days=r.getInt("days_recent");
        long recent=r.getLong("recent_views"), historical=r.getLong("historical_views");
        Long previous=nullableLong(r,"previous_views");
        Double retention=nullableDouble(r,"recent_retention");
        Double historicalRetention=nullableDouble(r,"historical_retention");
        Double peer=nullableDouble(r,"peer_retention");
        int peers=r.getInt("peer_count");
        var evidence=new ArrayList<Evidence>();
        var missing=new ArrayList<String>();
        evidence.add(new Evidence("recent_views",(double)recent,null,"last 28 complete days","Observed views; zero does not establish cause"));
        if(previous!=null) evidence.add(new Evidence("previous_views",previous.doubleValue(),null,"preceding 28 days","Comparison period"));
        if(historical>0) evidence.add(new Evidence("historical_views",(double)historical,null,"available ingested history","Not necessarily lifetime views"));
        if(retention!=null) evidence.add(new Evidence("average_view_percentage",retention,peers>=2?peer:null,
            "last 28 complete days","View-weighted daily average"));
        if(historicalRetention!=null) evidence.add(new Evidence("historical_average_view_percentage",historicalRetention,null,
            "available ingested history","View-weighted historical average"));
        if(days<7) missing.add("Fewer than seven observed recent metric days");
        if(previous==null) missing.add("Previous 28-day view comparison unavailable");
        if(days<21) missing.add("Fewer than 21 observed recent metric days; gaps may represent missing reporting rather than zero views");
        if(retention==null) missing.add("Recent audience-retention average unavailable");
        if(peers<2) missing.add("At least two comparable videos with recent retention are required for peer comparison");
        if("SHORT_OR_BRIEF".equals(format)) missing.add("Duration alone cannot verify YouTube Shorts classification");
        if("UNKNOWN".equals(format)) missing.add("Video duration unavailable; format-specific comparison unavailable");
        missing.add("Impressions and CTR not ingested; packaging performance cannot be diagnosed");
        missing.add("Traffic-source history not ingested; cause of traffic changes cannot be established");
        if(historical==0 && days==0) return new Diagnosis(id,format,"INSUFFICIENT_DATA",
            "No daily analytics available","No cause inferred",0,List.copyOf(evidence),List.copyOf(missing));

        String observation="No evidence-backed performance anomaly detected";
        String status="NO_CLEAR_ANOMALY";
        if(previous!=null && previous>=30 && recent*2L<=previous && days>=21) {
            status="DECLINING_TRAFFIC";
            observation="Recent views fell by at least 50% versus the preceding 28 days";
        } else if(retention!=null && peer!=null && peers>=2 && recent>=100 && days>=21 && retention<peer*0.75) {
            status="LOW_RELATIVE_RETENTION";
            observation="Recent average viewed percentage is below 75% of comparable-duration peers";
        } else if(days<21) {
            status="INSUFFICIENT_DATA";
            observation="Insufficient observed recent days to assess recent performance reliably";
        }
        double confidence=days<21?0:Math.min(0.85,0.3+0.25*Math.min(1,recent/100.0)
            +0.2*(previous==null?0:1)+0.1*(peers>=2?1:0));
        return new Diagnosis(id,format,status,observation,
            "Potential contributing causes require reach, traffic-source and retention-curve evidence; no causal conclusion made",
            Math.round(confidence*1000)/1000.0,List.copyOf(evidence),List.copyOf(missing));
    }

    private static Long nullableLong(ResultSet r,String name) throws Exception {
        long v=r.getLong(name); return r.wasNull()?null:v;
    }
    private static Double nullableDouble(ResultSet r,String name) throws Exception {
        double v=r.getDouble(name); return r.wasNull()?null:v;
    }
}
