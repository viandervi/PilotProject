package com.creatorrx.recommendation;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.postgresql.ds.PGSimpleDataSource;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceDiagnosisIntegrationTest {
    @Test void diagnosesMissingDataWithoutFabricatingTrafficCausesAndEnforcesOwnership() throws Exception {
        try(var db=new PostgreSQLContainer<>("postgres:16-alpine")) {
            db.start();
            Flyway.configure().dataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword()).load().migrate();
            UUID creator=UUID.randomUUID(), channel=UUID.randomUUID(), video=UUID.randomUUID();
            try(var conn=DriverManager.getConnection(db.getJdbcUrl(),db.getUsername(),db.getPassword())) {
                try(var st=conn.prepareStatement("INSERT INTO creator(id,google_subject) VALUES (?,'fr03-test')")) {
                    st.setObject(1,creator); st.executeUpdate();
                }
                try(var st=conn.prepareStatement("INSERT INTO youtube_channel(id,creator_id,youtube_channel_id,title) VALUES (?,?,'UC_fr03','test')")) {
                    st.setObject(1,channel); st.setObject(2,creator); st.executeUpdate();
                }
                try(var st=conn.prepareStatement("INSERT INTO video(id,channel_id,youtube_video_id,title,duration_seconds) VALUES (?,?,'abcdefghijk','test',600)")) {
                    st.setObject(1,video); st.setObject(2,channel); st.executeUpdate();
                }
            }
            var source=new PGSimpleDataSource();
            source.setURL(db.getJdbcUrl()); source.setUser(db.getUsername()); source.setPassword(db.getPassword());
            var service=new PerformanceDiagnosisService(source);
            var diagnosis=service.forChannel(creator,channel).getFirst();
            assertEquals(video,diagnosis.videoId());
            assertEquals("LONG_FORM",diagnosis.format());
            assertEquals("INSUFFICIENT_DATA",diagnosis.status());
            assertTrue(diagnosis.missingEvidence().stream().anyMatch(x->x.contains("CTR")));
            assertThrows(SecurityException.class,()->service.forChannel(UUID.randomUUID(),channel));
        }
    }
}
