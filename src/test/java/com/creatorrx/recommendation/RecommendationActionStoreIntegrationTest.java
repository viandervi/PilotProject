package com.creatorrx.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RecommendationActionStoreIntegrationTest {
    @Test void refreshPreservesCreatorActionsAndRejectsCrossCreatorWrites() throws Exception {
        try (var pg=new PostgreSQLContainer<>("postgres:16-alpine")) {
            pg.start();
            Flyway.configure().dataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword()).load().migrate();
            var ds=new PGSimpleDataSource();
            ds.setURL(pg.getJdbcUrl()); ds.setUser(pg.getUsername()); ds.setPassword(pg.getPassword());
            UUID owner=UUID.randomUUID(),other=UUID.randomUUID(),channel=UUID.randomUUID(),video=UUID.randomUUID();
            try (var c=DriverManager.getConnection(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword())) {
                try (var st=c.prepareStatement("INSERT INTO creator(id,google_subject) VALUES (?,?)")) {
                    st.setObject(1,owner); st.setString(2,"owner"); st.executeUpdate();
                    st.setObject(1,other); st.setString(2,"other"); st.executeUpdate();
                }
                try (var st=c.prepareStatement("INSERT INTO youtube_channel(id,creator_id,youtube_channel_id,title) VALUES (?,?,?,?)")) {
                    st.setObject(1,channel); st.setObject(2,owner); st.setString(3,"UCaction"); st.setString(4,"Channel"); st.executeUpdate();
                }
                try (var st=c.prepareStatement("INSERT INTO video(id,channel_id,youtube_video_id,title) VALUES (?,?,?,?)")) {
                    st.setObject(1,video); st.setObject(2,channel); st.setString(3,"actionVideo"); st.setString(4,"Video"); st.executeUpdate();
                }
            }
            var store=new RecommendationActionStore(ds,new ObjectMapper());
            var recommendation=new Recommendation(video,Recommendation.Decision.FIX,70,0.8,List.of("Low retention"));
            assertThrows(SecurityException.class,()->store.save(other,channel,List.of(recommendation)));
            store.save(owner,channel,List.of(recommendation));
            store.save(owner,channel,List.of(recommendation));
            assertEquals(1,store.list(owner,channel).size());
            assertTrue(store.list(other,channel).isEmpty());
            assertFalse(store.updateStatus(other,channel,video,"DISMISSED"));
            assertTrue(store.updateStatus(owner,channel,video,"APPLIED"));
            store.save(owner,channel,List.of(recommendation));
            assertEquals("APPLIED",store.list(owner,channel).getFirst().status());
            assertThrows(IllegalArgumentException.class,()->store.updateStatus(owner,channel,video,"INVALID"));
        }
    }
}
