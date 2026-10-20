package com.creatorrx.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.connection.annotation.Connectable;
import jakarta.inject.Singleton;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Singleton
@Requires(env = "database")
@Connectable
public class RecommendationActionStore {
    private final DataSource dataSource;
    private final ObjectMapper mapper;
    public RecommendationActionStore(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper = mapper;
    }
    public record Action(UUID videoId, String decision, double score, double confidence,
                         List<String> reasons, String status) {}

    /** Only actionable recommendations are persisted; existing creator decisions are preserved. */
    public void save(UUID creator, UUID channel, List<Recommendation> recommendations) throws Exception {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var ownership = connection.prepareStatement(
                        "SELECT 1 FROM youtube_channel WHERE id=? AND creator_id=?")) {
                    ownership.setObject(1, channel); ownership.setObject(2, creator);
                    try (var rows = ownership.executeQuery()) {
                        if (!rows.next()) throw new SecurityException("Channel not owned by creator");
                    }
                }
                String sql = """
                    INSERT INTO recommendation_action(video_id,decision,score,confidence,reasons)
                    SELECT v.id,?,?,?,? FROM video v WHERE v.id=? AND v.channel_id=?
                    ON CONFLICT (video_id) DO UPDATE SET
                        decision=EXCLUDED.decision, score=EXCLUDED.score,
                        confidence=EXCLUDED.confidence, reasons=EXCLUDED.reasons,
                        generated_at=now()
                    WHERE recommendation_action.status='OPEN'
                    """;
                try (var stmt = connection.prepareStatement(sql)) {
                    for (var recommendation : recommendations) {
                        if (recommendation.decision() != Recommendation.Decision.FIX
                                && recommendation.decision() != Recommendation.Decision.REVIVE) continue;
                        stmt.setString(1, recommendation.decision().name());
                        stmt.setDouble(2, recommendation.score());
                        stmt.setDouble(3, recommendation.confidence());
                        stmt.setString(4, mapper.writeValueAsString(recommendation.reasons()));
                        stmt.setObject(5, recommendation.videoId());
                        stmt.setObject(6, channel);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public List<Action> list(UUID creator, UUID channel) throws Exception {
        String sql = """
            SELECT r.video_id,r.decision,r.score,r.confidence,r.reasons,r.status
            FROM recommendation_action r JOIN video v ON v.id=r.video_id
            JOIN youtube_channel c ON c.id=v.channel_id
            WHERE c.id=? AND c.creator_id=? ORDER BY r.score DESC, r.video_id
            """;
        var actions = new ArrayList<Action>();
        try (var connection=dataSource.getConnection(); var stmt=connection.prepareStatement(sql)) {
            stmt.setObject(1,channel); stmt.setObject(2,creator);
            try (var rows=stmt.executeQuery()) {
                while(rows.next()) {
                    List<String> reasons=mapper.readValue(rows.getString("reasons"),
                            mapper.getTypeFactory().constructCollectionType(List.class,String.class));
                    actions.add(new Action(rows.getObject("video_id",UUID.class),
                            rows.getString("decision"),rows.getDouble("score"),
                            rows.getDouble("confidence"),reasons,rows.getString("status")));
                }
            }
        }
        return List.copyOf(actions);
    }

    public boolean updateStatus(UUID creator, UUID channel, UUID video, String status) throws Exception {
        if (!List.of("OPEN","APPLIED","DISMISSED").contains(status))
            throw new IllegalArgumentException("Invalid recommendation status");
        String sql = """
            UPDATE recommendation_action r SET status=?,status_updated_at=now()
            FROM video v JOIN youtube_channel c ON c.id=v.channel_id
            WHERE r.video_id=v.id AND v.id=? AND c.id=? AND c.creator_id=?
            """;
        try (var connection=dataSource.getConnection(); var stmt=connection.prepareStatement(sql)) {
            stmt.setString(1,status); stmt.setObject(2,video);
            stmt.setObject(3,channel); stmt.setObject(4,creator);
            return stmt.executeUpdate()==1;
        }
    }
}
