package com.creatorrx;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PocTest {
    @Test void analyticsUsesOwnChannelAndCorrectMetricSets() {
        var performance = YouTubeController.analyticsParams("abcdefghijk", "2026-01-01", "2026-02-01", false);
        assertEquals("channel==MINE", performance.get("ids"));
        assertEquals("video==abcdefghijk", performance.get("filters"));
        assertTrue(performance.get("metrics").contains("estimatedMinutesWatched"));
        var retention = YouTubeController.analyticsParams("abcdefghijk", "2026-01-01", "2026-02-01", true);
        assertEquals("elapsedVideoTimeRatio", retention.get("dimensions"));
        assertEquals("audienceWatchRatio,relativeRetentionPerformance", retention.get("metrics"));
    }
    @Test void invalidQueriesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> YouTubeController.analyticsParams("bad", "2026-01-01", "2026-02-01", false));
        assertThrows(IllegalArgumentException.class, () -> YouTubeController.analyticsParams("abcdefghijk", "2026-02-01", "2026-01-01", false));
    }
    @Test void bearerTokensCannotBeSentToArbitraryDownloadHosts() {
        assertThrows(IllegalArgumentException.class, () -> GoogleGateway.validateDownload("https://evil.example/report"));
        assertThrows(IllegalArgumentException.class, () -> GoogleGateway.validateDownload("http://youtubereporting.googleapis.com/report"));
        assertThrows(IllegalArgumentException.class, () -> GoogleGateway.validateDownload("https://youtubereporting.googleapis.com:444/report"));
        assertEquals("youtubereporting.googleapis.com", GoogleGateway.validateDownload("https://youtubereporting.googleapis.com/v1/media/x").getHost());
    }
    @Test void callbackRejectsUnsolicitedState() {
        assertThrows(IllegalArgumentException.class, () -> new GoogleGateway().callback("unsolicited", "code"));
    }
}
