package com.creatorrx.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnalyticsMetricStoreTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void validatesRetentionColumnOrder() throws Exception {
        var correct = mapper.readTree("""
            {"columnHeaders":[{"name":"elapsedVideoTimeRatio"},{"name":"audienceWatchRatio"},{"name":"relativeRetentionPerformance"}],"rows":[]}
            """);
        assertDoesNotThrow(() -> AnalyticsMetricStore.validateReport(correct,
            "elapsedVideoTimeRatio", "audienceWatchRatio", "relativeRetentionPerformance"));
        var swapped = mapper.readTree("""
            {"columnHeaders":[{"name":"audienceWatchRatio"},{"name":"elapsedVideoTimeRatio"},{"name":"relativeRetentionPerformance"}],"rows":[]}
            """);
        assertThrows(IllegalArgumentException.class, () -> AnalyticsMetricStore.validateReport(swapped,
            "elapsedVideoTimeRatio", "audienceWatchRatio", "relativeRetentionPerformance"));
    }

    @Test void rejectsMalformedRowsButAllowsMissingRowsForNoData() throws Exception {
        var noRows = mapper.readTree("""
            {"columnHeaders":[{"name":"elapsedVideoTimeRatio"},{"name":"audienceWatchRatio"},{"name":"relativeRetentionPerformance"}]}
            """);
        assertDoesNotThrow(() -> AnalyticsMetricStore.validateReport(noRows,
            "elapsedVideoTimeRatio", "audienceWatchRatio", "relativeRetentionPerformance"));
        var invalid = mapper.readTree("""
            {"columnHeaders":[{"name":"elapsedVideoTimeRatio"},{"name":"audienceWatchRatio"},{"name":"relativeRetentionPerformance"}],"rows":{}}
            """);
        assertThrows(IllegalArgumentException.class, () -> AnalyticsMetricStore.validateReport(invalid,
            "elapsedVideoTimeRatio", "audienceWatchRatio", "relativeRetentionPerformance"));
    }
}
