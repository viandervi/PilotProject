package com.creatorrx.ingestion;

import com.creatorrx.auth.CreatorGoogleApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.UUID;

@Singleton
@Requires(env = "database")
public class ChannelDiscoveryService {
    private final CreatorGoogleApiClient google;
    private final ChannelCatalogStore catalog;
    public ChannelDiscoveryService(CreatorGoogleApiClient google, ChannelCatalogStore catalog) {
        this.google = google; this.catalog = catalog;
    }
    public UUID discover(UUID creatorId) throws Exception {
        JsonNode channels = google.get(creatorId, "youtube", "/youtube/v3/channels",
            Map.of("part", "snippet,contentDetails", "mine", "true")).path("items");
        if (channels.isEmpty()) throw new IllegalStateException("No YouTube channel connected");
        JsonNode channel = channels.get(0);
        String id = channel.path("id").asText("");
        if (id.isBlank()) throw new IllegalStateException("Google returned no channel ID");
        return catalog.upsertChannel(creatorId, id,
            channel.path("snippet").path("title").asText(""),
            channel.path("contentDetails").path("relatedPlaylists").path("uploads").asText(""));
    }
}
