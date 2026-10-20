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
    private final com.creatorrx.dashboard.DashboardQueryService dashboard;
    private final CreatorGoogleApiClient google;
    private final CreatorTokenProvider tokens;
    private final com.creatorrx.ingestion.ChannelDiscoveryService discovery;
    private final com.creatorrx.ingestion.ChannelSyncQueue queue;
    private final com.creatorrx.ingestion.SyncJobStore syncJobs;
    private final com.creatorrx.ingestion.AnalyticsSyncQueue analyticsQueue;
    private final com.creatorrx.ingestion.RetentionSyncQueue retentionQueue;
    private final com.creatorrx.ingestion.AnalyticsDataQualityService quality;
    private final com.creatorrx.recommendation.RecommendationService recommendations;
    private final com.creatorrx.recommendation.RecommendationActionStore actions;
    public CreatorApiController(CreatorSessionService sessions, CreatorGoogleApiClient google, CreatorTokenProvider tokens,
                                com.creatorrx.ingestion.ChannelDiscoveryService discovery,
                                com.creatorrx.ingestion.ChannelSyncQueue queue,
                                com.creatorrx.ingestion.SyncJobStore syncJobs,
                                com.creatorrx.ingestion.AnalyticsSyncQueue analyticsQueue,
                                com.creatorrx.ingestion.RetentionSyncQueue retentionQueue,
                                com.creatorrx.ingestion.AnalyticsDataQualityService quality,
                                com.creatorrx.recommendation.RecommendationService recommendations,
                                com.creatorrx.recommendation.RecommendationActionStore actions,
                                com.creatorrx.dashboard.DashboardQueryService dashboard) {
        this.sessions = sessions; this.google = google; this.tokens = tokens;
        this.discovery = discovery; this.queue = queue; this.syncJobs = syncJobs; this.analyticsQueue = analyticsQueue; this.retentionQueue = retentionQueue; this.quality = quality; this.recommendations = recommendations; this.actions = actions; this.dashboard = dashboard;
    }

    @Get("/dashboard/overview")
    public com.creatorrx.dashboard.DashboardQueryService.Overview dashboardOverview(
            @CookieValue(value="creatorrx_session", defaultValue="") String session) throws Exception {
        return dashboard.overview(sessions.requireCreator(session));
    }

    @Get("/channels/{channelId}/dashboard-videos")
    public java.util.List<com.creatorrx.dashboard.DashboardQueryService.Video> dashboardVideos(
            @CookieValue(value="creatorrx_session", defaultValue="") String session,
            @PathVariable UUID channelId) throws Exception {
        return dashboard.videos(sessions.requireCreator(session), channelId);
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

    @Post("/channels/sync")
    public Map<String,String> sync(@CookieValue(value="creatorrx_session", defaultValue="") String session) throws Exception {
        UUID creator = sessions.requireCreator(session);
        UUID channel = discovery.discover(creator);
        UUID job = queue.enqueue(creator, channel);
        return Map.of("syncId", job.toString(), "channelId", channel.toString(), "status", "QUEUED");
    }

    @Post("/channels/analytics-sync")
    public Map<String,String> analyticsSync(@CookieValue(value="creatorrx_session", defaultValue="") String session) throws Exception {
        UUID creator = sessions.requireCreator(session);
        UUID channel = discovery.discover(creator);
        UUID job = analyticsQueue.enqueue(creator, channel);
        return Map.of("syncId", job.toString(), "channelId", channel.toString(), "status", "QUEUED");
    }

    @Post("/channels/retention-sync")
    public Map<String,String> retentionSync(@CookieValue(value="creatorrx_session", defaultValue="") String session) throws Exception {
        UUID creator = sessions.requireCreator(session);
        UUID channel = discovery.discover(creator);
        UUID job = retentionQueue.enqueue(creator, channel);
        return Map.of("syncId", job.toString(), "channelId", channel.toString(), "status", "QUEUED");
    }

    @Get("/channels/{channelId}/sync-status")
    public java.util.List<com.creatorrx.ingestion.SyncJobStore.JobStatus> syncStatus(
            @CookieValue(value="creatorrx_session", defaultValue="") String session,
            @PathVariable UUID channelId) throws Exception {
        return syncJobs.latestForCreator(sessions.requireCreator(session), channelId);
    }

    @Get("/channels/{channelId}/analytics-coverage")
    public com.creatorrx.ingestion.AnalyticsDataQualityService.Coverage coverage(
            @CookieValue(value="creatorrx_session", defaultValue="") String session,
            @PathVariable UUID channelId) throws Exception {
        return quality.coverage(sessions.requireCreator(session), channelId);
    }

    @Get("/channels/{channelId}/recommendations")
    public java.util.List<com.creatorrx.recommendation.Recommendation> recommendations(
            @CookieValue(value="creatorrx_session", defaultValue="") String session,
            @PathVariable UUID channelId) throws Exception {
        return recommendations.forChannel(sessions.requireCreator(session), channelId);
    }


    @Post("/channels/{channelId}/recommendations/refresh")
    public java.util.List<com.creatorrx.recommendation.RecommendationActionStore.Action> refreshRecommendations(
            @CookieValue(value="creatorrx_session", defaultValue="") String session,
            @PathVariable UUID channelId) throws Exception {
        UUID creator = sessions.requireCreator(session);
        actions.save(creator, channelId, recommendations.forChannel(creator, channelId));
        return actions.list(creator, channelId);
    }

    @Get("/channels/{channelId}/recommendation-actions")
    public java.util.List<com.creatorrx.recommendation.RecommendationActionStore.Action> recommendationActions(
            @CookieValue(value="creatorrx_session", defaultValue="") String session,
            @PathVariable UUID channelId) throws Exception {
        return actions.list(sessions.requireCreator(session), channelId);
    }

    public record StatusUpdate(String status) {}

    @Patch("/channels/{channelId}/recommendation-actions/{videoId}")
    public HttpResponse<?> updateRecommendationStatus(
            @CookieValue(value="creatorrx_session", defaultValue="") String session,
            @PathVariable UUID channelId, @PathVariable UUID videoId,
            @Body StatusUpdate update) throws Exception {
        if (update == null || update.status() == null)
            return HttpResponse.badRequest();
        String status = update.status().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.List.of("OPEN","APPLIED","DISMISSED").contains(status))
            return HttpResponse.badRequest();
        boolean changed = actions.updateStatus(sessions.requireCreator(session), channelId, videoId, status);
        return changed ? HttpResponse.noContent() : HttpResponse.notFound();
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
