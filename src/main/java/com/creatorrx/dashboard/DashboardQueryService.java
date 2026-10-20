package com.creatorrx.dashboard;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.connection.annotation.Connectable;
import jakarta.inject.Singleton;
import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Singleton
@Requires(env = "database")
@Connectable
public class DashboardQueryService {
    private final DataSource dataSource;
    public DashboardQueryService(DataSource dataSource) { this.dataSource = dataSource; }
    public record Channel(UUID id, String title, String youtubeChannelId, long videoCount) {}
    public record Overview(List<Channel> channels, long totalVideos, long totalRecommendations,
                           long openFix, long openRevive, long applied) {}
    public record Video(UUID id, String youtubeVideoId, String title, String publishedAt,
                        long views28d, Double averageViewPercentage) {}

    public Overview overview(UUID creator) throws Exception {
        var channels=new ArrayList<Channel>();
        String channelSql="""
            SELECT c.id,c.title,c.youtube_channel_id,COUNT(v.id) video_count
            FROM youtube_channel c LEFT JOIN video v ON v.channel_id=c.id
            WHERE c.creator_id=? GROUP BY c.id,c.title,c.youtube_channel_id ORDER BY c.created_at
            """;
        try(var conn=dataSource.getConnection()) {
            try(var stmt=conn.prepareStatement(channelSql)) {
                stmt.setObject(1,creator);
                try(var rows=stmt.executeQuery()) {
                    while(rows.next()) channels.add(new Channel(rows.getObject("id",UUID.class),
                            rows.getString("title"),rows.getString("youtube_channel_id"),
                            rows.getLong("video_count")));
                }
            }
            String counts="""
                SELECT COUNT(*) total,
                COUNT(*) FILTER (WHERE r.decision='FIX' AND r.status='OPEN') fix,
                COUNT(*) FILTER (WHERE r.decision='REVIVE' AND r.status='OPEN') revive,
                COUNT(*) FILTER (WHERE r.status='APPLIED') applied
                FROM recommendation_action r JOIN video v ON v.id=r.video_id
                JOIN youtube_channel c ON c.id=v.channel_id WHERE c.creator_id=?
                """;
            try(var stmt=conn.prepareStatement(counts)) {
                stmt.setObject(1,creator);
                try(var rows=stmt.executeQuery()) {
                    rows.next();
                    return new Overview(List.copyOf(channels),
                            channels.stream().mapToLong(Channel::videoCount).sum(),
                            rows.getLong("total"),rows.getLong("fix"),
                            rows.getLong("revive"),rows.getLong("applied"));
                }
            }
        }
    }

    public List<Video> videos(UUID creator,UUID channel) throws Exception {
        String sql="""
            SELECT v.id,v.youtube_video_id,v.title,v.published_at,
                   COALESCE(SUM(m.views),0) views_28d,
                   SUM(m.average_view_percentage*m.views) /
                       NULLIF(SUM(m.views) FILTER (WHERE m.average_view_percentage IS NOT NULL),0) avg_viewed
            FROM video v JOIN youtube_channel c ON c.id=v.channel_id
            LEFT JOIN video_daily_metric m ON m.video_id=v.id AND m.metric_date >= ?
                AND m.metric_date < ?
            WHERE c.creator_id=? AND c.id=?
            GROUP BY v.id,v.youtube_video_id,v.title,v.published_at
            ORDER BY views_28d DESC,v.id LIMIT 500
            """;
        var list=new ArrayList<Video>();
        LocalDate end=LocalDate.now(ZoneOffset.UTC).minusDays(1);
        try(var conn=dataSource.getConnection();var stmt=conn.prepareStatement(sql)) {
            stmt.setObject(1,end.minusDays(28));stmt.setObject(2,end);
            stmt.setObject(3,creator);stmt.setObject(4,channel);
            try(var rows=stmt.executeQuery()) {
                while(rows.next()) {
                    var published=rows.getTimestamp("published_at");
                    var avg=rows.getObject("avg_viewed");
                    list.add(new Video(rows.getObject("id",UUID.class),rows.getString("youtube_video_id"),
                            rows.getString("title"),published==null?null:published.toInstant().toString(),
                            rows.getLong("views_28d"),avg==null?null:((Number)avg).doubleValue()));
                }
            }
        }
        return List.copyOf(list);
    }
}
