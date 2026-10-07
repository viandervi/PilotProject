package com.creatorrx;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ApiFlowTest {
    static class FakeGoogle extends GoogleGateway {
        String lastPath; Map<String,String> lastParams;
        @Override public JsonNode get(String path, Map<String,String> params) throws Exception {
            lastPath = path; lastParams = params;
            var mapper = new ObjectMapper();
            if (path.endsWith("/channels")) return mapper.readTree("{\"items\":[{\"contentDetails\":{\"relatedPlaylists\":{\"uploads\":\"UU-test\"}}}]}");
            if (path.endsWith("/reportTypes")) return mapper.readTree("{\"reportTypes\":[{\"id\":\"channel_reach_test\"}]}");
            if (path.endsWith("/reports/report1")) return mapper.readTree("{\"downloadUrl\":\"https://youtubereporting.googleapis.com/v1/media/test\"}");
            return mapper.readTree("{\"items\":[],\"nextPageToken\":\"page2\"}");
        }
        @Override public JsonNode post(String path, Map<String,String> body) throws Exception {
            lastPath = path; lastParams = body;
            return new ObjectMapper().readTree("{\"id\":\"job1\"}");
        }
        @Override public String download(String url) { validateDownload(url); return "video_id,video_thumbnail_impressions,video_thumbnail_impressions_ctr\nabcdefghijk,1000,0.05\n"; }
    }
    @Test void uploadsUsePlaylistAndPreservePagination() throws Exception {
        var google = new FakeGoogle(); var controller = new YouTubeController(google);
        assertEquals("page2", controller.videos("page1").path("nextPageToken").asText());
        assertTrue(google.lastPath.endsWith("/playlistItems"));
        assertEquals("UU-test", google.lastParams.get("playlistId"));
        assertEquals("page1", google.lastParams.get("pageToken"));
    }
    @Test void reachJobRequiresAdvertisedTypeAndCsvIsUnmodified() throws Exception {
        var google = new FakeGoogle(); var controller = new YouTubeController(google);
        assertThrows(IllegalArgumentException.class, () -> controller.reach("channel_reach_invented"));
        assertEquals("job1", controller.reach("channel_reach_test").path("id").asText());
        assertEquals("channel_reach_test", google.lastParams.get("reportTypeId"));
        assertTrue(controller.download("job1", "report1").contains("abcdefghijk,1000,0.05"));
    }
}
