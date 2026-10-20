package com.creatorrx;

import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.annotation.Error;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;

@Controller
@ExecuteOn(TaskExecutors.BLOCKING)
public class YouTubeController {
    private final GoogleGateway google;
    private final io.micronaut.context.env.Environment environment;
    private final com.creatorrx.auth.CreatorIdentityStore identities;
    private final com.creatorrx.auth.GoogleIdentityVerifier verifier;
    private final com.creatorrx.auth.OAuthCredentialStore credentials;
    @jakarta.inject.Inject
    public YouTubeController(GoogleGateway google, io.micronaut.context.env.Environment environment,
            @io.micronaut.core.annotation.Nullable com.creatorrx.auth.CreatorIdentityStore identities,
            @io.micronaut.core.annotation.Nullable com.creatorrx.auth.GoogleIdentityVerifier verifier,
            @io.micronaut.core.annotation.Nullable com.creatorrx.auth.OAuthCredentialStore credentials) {
        this.google = google; this.environment = environment; this.identities = identities;
        this.verifier = verifier; this.credentials = credentials;
    }
    /** Preserves direct POC test construction; Micronaut uses the injected constructor. */
    public YouTubeController(GoogleGateway google) {
        this(google, null, null, null, null);
    }
    @Get("/health") public Map<String,String> health() { return Map.of("status", "ok", "mode", "single-user-local-poc"); }
    @Get("/auth/google") public HttpResponse<?> auth() throws Exception { return HttpResponse.seeOther(URI.create(google.authorize())); }
    @Get("/oauth/callback{?state,code}") public Map<String,String> callback(String state, String code) throws Exception {
        if (environment != null && environment.getActiveNames().contains("database")) {
            var exchange = google.exchangeForCreator(state, code);
            String subject = verifier.verifySubject(exchange.idToken(), exchange.nonce());
            UUID creatorId = identities.findOrCreate(subject);
            credentials.save(creatorId, exchange.refreshToken());
            return Map.of("status", "connected", "creatorId", creatorId.toString(), "next", "authenticated creator-scoped APIs");
        }
        google.callback(state, code); return Map.of("status", "connected", "next", "/youtube/channel");
    }
    @Get("/youtube/channel") public JsonNode channel() throws Exception {
        requirePocMode(); return google.get("www.googleapis.com/youtube/v3/channels", Map.of("part", "snippet,contentDetails,statistics", "mine", "true"));
    }
    @Get("/youtube/videos{?pageToken}") public JsonNode videos(@QueryValue(defaultValue="") String pageToken) throws Exception {
        requirePocMode(); JsonNode channels = channel().path("items");
        if (channels.isEmpty()) throw new IllegalStateException("No YouTube channel found for this Google account.");
        String uploads = channels.get(0).path("contentDetails").path("relatedPlaylists").path("uploads").asText();
        // Follow nextPageToken to enumerate the whole uploads playlist. Avoid costly search.list.
        return google.get("www.googleapis.com/youtube/v3/playlistItems", Map.of("part", "snippet,contentDetails", "playlistId", uploads, "maxResults", "50", "pageToken", pageToken));
    }
    @Get("/youtube/videos/{videoId}/details") public JsonNode details(String videoId) throws Exception {
        requirePocMode(); video(videoId); return google.get("www.googleapis.com/youtube/v3/videos", Map.of("part", "snippet,contentDetails,statistics", "id", videoId));
    }
    static void video(String id) { if (!id.matches("[A-Za-z0-9_-]{11}")) throw new IllegalArgumentException("Expected an 11-character YouTube video ID."); }
    static Map<String,String> analyticsParams(String id, String start, String end, boolean retention) {
        video(id); LocalDate first = LocalDate.parse(start), last = LocalDate.parse(end);
        if (first.isAfter(last) || last.isAfter(LocalDate.now())) throw new IllegalArgumentException("Use an ordered date range ending today or earlier.");
        return Map.of("ids", "channel==MINE", "startDate", start, "endDate", end, "filters", "video==" + id,
            "dimensions", retention ? "elapsedVideoTimeRatio" : "day",
            "metrics", retention ? "audienceWatchRatio,relativeRetentionPerformance" : "views,estimatedMinutesWatched,averageViewDuration,averageViewPercentage,subscribersGained");
    }
    @Get("/youtube/analytics/{videoId}{?startDate,endDate}") public JsonNode analytics(String videoId, String startDate, String endDate) throws Exception {
        requirePocMode(); return google.get("youtubeanalytics.googleapis.com/v2/reports", analyticsParams(videoId, startDate, endDate, false));
    }
    @Get("/youtube/analytics/{videoId}/retention{?startDate,endDate}") public JsonNode retention(String videoId, String startDate, String endDate) throws Exception {
        return google.get("youtubeanalytics.googleapis.com/v2/reports", analyticsParams(videoId, startDate, endDate, true));
    }
    @Get("/youtube/reporting/types{?pageToken}") public JsonNode types(@QueryValue(defaultValue="") String pageToken) throws Exception {
        requirePocMode(); return google.get("youtubereporting.googleapis.com/v1/reportTypes", Map.of("pageToken", pageToken));
    }
    @Get("/youtube/reporting/jobs{?pageToken}") public JsonNode jobs(@QueryValue(defaultValue="") String pageToken) throws Exception {
        return google.get("youtubereporting.googleapis.com/v1/jobs", Map.of("pageToken", pageToken));
    }
    @Post("/youtube/reporting/reach{?reportTypeId}") public JsonNode reach(String reportTypeId) throws Exception {
        requirePocMode(); // Discover actual availability; do not assume a report ID from the planning discussion exists.
        String page = "";
        do {
            JsonNode response = types(page);
            for (JsonNode type : response.path("reportTypes")) {
                if (type.path("id").asText().equals(reportTypeId) && reportTypeId.contains("reach")) {
                    return google.post("youtubereporting.googleapis.com/v1/jobs", Map.of("reportTypeId", reportTypeId, "name", "CreatorRx Reach POC"));
                }
            }
            page = response.path("nextPageToken").asText("");
        } while (!page.isEmpty());
        throw new IllegalArgumentException("Requested Reach report type is not available. Inspect /youtube/reporting/types; CTR feasibility remains unverified.");
    }
    static void resourceId(String id) { if (!id.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid report/job ID."); }
    @Get("/youtube/reporting/reports{?jobId,pageToken}") public JsonNode reports(String jobId, @QueryValue(defaultValue="") String pageToken) throws Exception {
        requirePocMode(); resourceId(jobId); return google.get("youtubereporting.googleapis.com/v1/jobs/" + jobId + "/reports", Map.of("pageToken", pageToken));
    }
    @Get(value="/youtube/reporting/download/{jobId}/{reportId}", produces="text/csv") public String download(String jobId, String reportId) throws Exception {
        requirePocMode(); resourceId(jobId); resourceId(reportId);
        JsonNode report = google.get("youtubereporting.googleapis.com/v1/jobs/" + jobId + "/reports/" + reportId, Map.of());
        return google.download(report.path("downloadUrl").asText());
    }
    private void requirePocMode() {
        if (environment.getActiveNames().contains("database"))
            throw new IllegalStateException("Legacy global-account endpoints are disabled in database mode; use authenticated creator-scoped APIs.");
    }
    @Error(global=true, exception=IllegalArgumentException.class) public HttpResponse<?> invalid(IllegalArgumentException e) {
        return HttpResponse.badRequest(Map.of("error", e.getMessage()));
    }
    @Error(global=true, exception=IllegalStateException.class) public HttpResponse<?> state(IllegalStateException e) {
        return HttpResponse.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }
    @Error(global=true, exception=GoogleGateway.GoogleFailure.class) public HttpResponse<?> upstream(GoogleGateway.GoogleFailure e) {
        return HttpResponse.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage(), "upstreamStatus", e.status));
    }
}
