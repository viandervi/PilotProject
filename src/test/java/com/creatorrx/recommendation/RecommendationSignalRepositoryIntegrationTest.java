package com.creatorrx.recommendation;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RecommendationSignalRepositoryIntegrationTest {
    @Test void loadsIsolatedCohortBaselinesAndEarlyRetention() throws Exception {
        try (var pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
            pg.start();
            Flyway.configure().dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()).load().migrate();
            var ds = new PGSimpleDataSource();
            ds.setURL(pg.getJdbcUrl()); ds.setUser(pg.getUsername()); ds.setPassword(pg.getPassword());
            UUID creator = UUID.randomUUID(), outsider = UUID.randomUUID(), channel = UUID.randomUUID();
            LocalDate date = LocalDate.of(2026, 10, 18);
            try (var c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())) {
                try (var st = c.prepareStatement("INSERT INTO creator(id,google_subject) VALUES (?,?)")) {
                    st.setObject(1,creator); st.setString(2,"owner"); st.executeUpdate();
                    st.setObject(1,outsider); st.setString(2,"outsider"); st.executeUpdate();
                }
                try (var st = c.prepareStatement("INSERT INTO youtube_channel(id,creator_id,youtube_channel_id,title) VALUES (?,?,?,?)")) {
                    st.setObject(1,channel); st.setObject(2,creator); st.setString(3,"UCtest"); st.setString(4,"Test"); st.executeUpdate();
                }
                for (int i=0;i<6;i++) {
                    UUID id=UUID.randomUUID();
                    try (var st=c.prepareStatement("INSERT INTO video(id,channel_id,youtube_video_id,title,published_at) VALUES (?,?,?,?,?)")) {
                        st.setObject(1,id); st.setObject(2,channel); st.setString(3,"video"+i); st.setString(4,"Test "+i);
                        st.setObject(5,java.time.OffsetDateTime.parse("2025-01-01T00:00:00Z")); st.executeUpdate();
                    }
                    for (int d=0;d<8;d++) {
                        try (var st=c.prepareStatement("INSERT INTO video_daily_metric(video_id,metric_date,views,watch_minutes,average_view_duration,average_view_percentage) VALUES (?,?,200,20,30,?)")) {
                            st.setObject(1,id); st.setObject(2,date.minusDays(d)); st.setInt(3,i==0?20:60); st.executeUpdate();
                        }
                    }
                    try (var st=c.prepareStatement("INSERT INTO video_retention_point(video_id,period_start,period_end,elapsed_ratio,audience_watch_ratio) VALUES (?,?,?,?,?)")) {
                        st.setObject(1,id); st.setObject(2,date.minusDays(20)); st.setObject(3,date);
                        st.setBigDecimal(4,new java.math.BigDecimal("0.20"));
                        st.setBigDecimal(5,new java.math.BigDecimal(i==0?"0.20":"0.80")); st.executeUpdate();
                    }
                }
            }
            var repository = new RecommendationSignalRepository(ds);
            assertThrows(SecurityException.class,()->repository.load(outsider,channel,date));
            java.util.List<VideoSignal> signals;
            try { signals=repository.load(creator,channel,date); }
            catch (java.sql.SQLException e) { System.err.println("Recommendation SQL failure: state="+e.getSQLState()+" message="+e.getMessage()); throw e; }
            assertEquals(6,signals.size());
            var low=signals.stream().filter(s->s.averageViewPercentage()!=null && s.averageViewPercentage()<30).findFirst().orElseThrow();
            assertEquals(60.0,low.cohortAverageViewPercentage(),0.01);
            assertEquals(0.8,low.cohortEarlyRetention(),0.01);
            assertEquals(0.2,low.earlyRetention(),0.01);
            assertEquals(8,low.observedDays());
            assertEquals(Recommendation.Decision.FIX,new RecommendationEngine().evaluate(low,date.plusDays(2)).decision());
        }
    }
}
