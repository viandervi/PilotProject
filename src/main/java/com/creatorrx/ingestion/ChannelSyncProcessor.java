package com.creatorrx.ingestion;

import com.creatorrx.auth.CreatorGoogleApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.connection.annotation.Connectable;
import jakarta.inject.Singleton;
import javax.sql.DataSource;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Fetches the owned channel's uploads playlist with resumable page checkpoints. */
@Singleton
@Requires(env = "database")
@Connectable
public class ChannelSyncProcessor implements SyncJobProcessor {
    private final DataSource datasource;
    private final CreatorGoogleApiClient google;
    private final ChannelCatalogStore catalog;
    private final SyncJobStore jobs;

    public ChannelSyncProcessor(DataSource datasource, CreatorGoogleApiClient google,
                                ChannelCatalogStore catalog, SyncJobStore jobs) {
        this.datasource = datasource; this.google = google; this.catalog = catalog; this.jobs = jobs;
    }
    @Override public String syncType() { return "CHANNEL_SYNC"; }

    @Override public void process(UUID channelId) throws Exception {
        throw new UnsupportedOperationException("Lease-bound claim required");
    }

    public void process(SyncJobStore.Claim claim) throws Exception {
        UUID channelId = claim.channelId();
        UUID creatorId;
        String youtubeId;
        try (var connection = datasource.getConnection();
             var stmt = connection.prepareStatement("SELECT creator_id, youtube_channel_id FROM youtube_channel WHERE id = ?")) {
            stmt.setObject(1, channelId);
            try (var rows = stmt.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Channel not found");
                creatorId = rows.getObject(1, UUID.class);
                youtubeId = rows.getString(2);
            }
        }
        JsonNode owned = google.get(creatorId, "youtube", "/youtube/v3/channels",
            Map.of("part", "snippet,contentDetails", "mine", "true")).path("items");
        JsonNode channel = null;
        for (JsonNode item : owned) if (youtubeId.equals(item.path("id").asText())) { channel = item; break; }
        if (channel == null) throw new SecurityException("Channel no longer owned by Google account");
        String uploads = channel.path("contentDetails").path("relatedPlaylists").path("uploads").asText("");
        if (uploads.isBlank()) throw new IllegalStateException("Uploads playlist unavailable");
        catalog.upsertChannel(creatorId, youtubeId, channel.path("snippet").path("title").asText(""), uploads);

        String page = jobs.cursor(claim);
        while (true) {
            JsonNode response = google.get(creatorId, "youtube", "/youtube/v3/playlistItems",
                Map.of("part", "snippet,contentDetails", "playlistId", uploads,
                    "maxResults", "50", "pageToken", page == null ? "" : page));
            int count = 0;
            for (JsonNode item : response.path("items")) {
                String id = item.path("contentDetails").path("videoId").asText("");
                if (!id.matches("[A-Za-z0-9_-]{11}")) continue;
                JsonNode snippet = item.path("snippet");
                String published = snippet.path("publishedAt").asText("");
                Instant date = published.isBlank() ? null : Instant.parse(published);
                catalog.upsertVideo(channelId, id, snippet.path("title").asText(""), date);
                count++;
            }
            String next = response.path("nextPageToken").asText("");
            if (!jobs.checkpoint(claim, next, count)) throw new IllegalStateException("Lost sync lease");
            if (next.isBlank()) return;
            if (next.equals(page)) throw new IllegalStateException("Repeated pagination cursor");
            page = next;
        }
    }
}
