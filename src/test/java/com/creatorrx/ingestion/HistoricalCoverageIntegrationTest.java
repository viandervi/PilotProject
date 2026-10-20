package com.creatorrx.ingestion;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalCoverageIntegrationTest {
    @Test void adjacentFetchedRangesCoverHistoricalWindowIncludingEmptyResults() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:16-alpine")) {
            db.start();
            Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).load().migrate();
            try (var conn = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword())) {
                UUID creator = UUID.randomUUID(), channel = UUID.randomUUID(), video = UUID.randomUUID();
                try (var st = conn.prepareStatement("INSERT INTO creator(id,google_subject) VALUES (?,?)")) {
                    st.setObject(1,creator); st.setString(2,"historical-coverage-test"); st.executeUpdate();
                }
                try (var st = conn.prepareStatement("INSERT INTO youtube_channel(id,creator_id,youtube_channel_id,title) VALUES (?,?,'UC_coverage','test')")) {
                    st.setObject(1,channel); st.setObject(2,creator); st.executeUpdate();
                }
                try (var st = conn.prepareStatement("INSERT INTO video(id,channel_id,youtube_video_id,title) VALUES (?,?,'abcdefghijk','test')")) {
                    st.setObject(1,video); st.setObject(2,channel); st.executeUpdate();
                }
                try (var st = conn.prepareStatement("INSERT INTO video_analytics_coverage(video_id,period_start,period_end) VALUES (?,?,?)")) {
                    for (int i=0;i<2;i++) {
                        st.setObject(1,video); st.setObject(2,LocalDate.of(2025,1,1).plusDays(i*30));
                        st.setObject(3,LocalDate.of(2025,1,30).plusDays(i*30)); st.executeUpdate();
                    }
                }
                try (var st = conn.prepareStatement("""
                    SELECT range_agg(daterange(period_start, period_end + 1, '[)'))
                      @> daterange(?::date, (?::date + 1), '[)')
                    FROM video_analytics_coverage WHERE video_id=?
                    """)) {
                    st.setObject(1,LocalDate.of(2025,1,10)); st.setObject(2,LocalDate.of(2025,2,20)); st.setObject(3,video);
                    try(var rs=st.executeQuery()) { assertTrue(rs.next()); assertTrue(rs.getBoolean(1)); }
                }
                var coverage = new AnalyticsDataQualityService(new org.postgresql.ds.PGSimpleDataSource() {{
                    setURL(db.getJdbcUrl()); setUser(db.getUsername()); setPassword(db.getPassword());
                }}).coverage(creator,channel);
                assertEquals(2,coverage.fetchedHistoricalRanges());
                assertNull(coverage.earliestDailyMetric());
            }
        }
    }
}
