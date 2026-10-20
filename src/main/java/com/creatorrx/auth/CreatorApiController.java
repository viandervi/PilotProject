package com.creatorrx.auth;

import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import java.util.Map;
import java.util.UUID;

/** Creator identity comes only from a server session, never a caller-supplied UUID. */
@Controller("/api/v1")
@Requires(env = "database")
@ExecuteOn(TaskExecutors.BLOCKING)
public class CreatorApiController {
    private final CreatorSessionService sessions;
    private final CreatorGoogleApiClient google;
    private final CreatorTokenProvider tokens;
    public CreatorApiController(CreatorSessionService sessions, CreatorGoogleApiClient google, CreatorTokenProvider tokens) {
        this.sessions = sessions; this.google = google; this.tokens = tokens;
    }

    @Get("/me")
    public Map<String,String> me(@CookieValue(value="creatorrx_session", defaultValue="") String session) {
        return Map.of("creatorId", sessions.requireCreator(session).toString());
    }

    @Get("/youtube/channel")
    public JsonNode channel(@CookieValue(value="creatorrx_session", defaultValue="") String session) throws Exception {
        UUID creator = sessions.requireCreator(session);
        return google.get(creator, "youtube", "/youtube/v3/channels",
            Map.of("part", "snippet,contentDetails,statistics", "mine", "true"));
    }

    @Get("/youtube/videos{?pageToken}")
    public JsonNode videos(@CookieValue(value="creatorrx_session", defaultValue="") String session,
                           @QueryValue(defaultValue="") String pageToken) throws Exception {
        UUID creator = sessions.requireCreator(session);
        JsonNode channels = google.get(creator, "youtube", "/youtube/v3/channels",
            Map.of("part", "contentDetails", "mine", "true")).path("items");
        if (channels.isEmpty()) throw new IllegalStateException("No connected YouTube channel");
        String uploads = channels.get(0).path("contentDetails").path("relatedPlaylists").path("uploads").asText("");
        if (uploads.isBlank()) throw new IllegalStateException("Missing uploads playlist");
        return google.get(creator, "youtube", "/youtube/v3/playlistItems",
            Map.of("part", "snippet,contentDetails", "playlistId", uploads, "maxResults", "50", "pageToken", pageToken));
    }

    @Post("/disconnect")
    public HttpResponse<?> disconnect(@CookieValue(value="creatorrx_session", defaultValue="") String session) throws Exception {
        UUID creator = sessions.requireCreator(session);
        tokens.disconnect(creator);
        sessions.revoke(session);
        return HttpResponse.noContent();
    }

    @io.micronaut.http.annotation.Error(exception=SecurityException.class)
    public HttpResponse<?> unauthorized(SecurityException exception) {
        return HttpResponse.unauthorized();
    }
}
