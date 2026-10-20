package com.creatorrx.ingestion;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class AnalyticsSyncProcessorTest {
    @Test void oldVideosUseFourteenMonthWindow() {
        LocalDate end = LocalDate.of(2026, 10, 18);
        assertEquals(LocalDate.of(2025, 8, 19),
            AnalyticsSyncProcessor.historicalStart(LocalDate.of(2020, 1, 1), end));
    }

    @Test void recentVideosStartAtPublication() {
        LocalDate end = LocalDate.of(2026, 10, 18);
        assertEquals(LocalDate.of(2026, 9, 10),
            AnalyticsSyncProcessor.historicalStart(LocalDate.of(2026, 9, 10), end));
    }

    @Test void unknownPublicationUsesWindowStart() {
        LocalDate end = LocalDate.of(2026, 10, 18);
        assertEquals(end.plusDays(1).minusMonths(14),
            AnalyticsSyncProcessor.historicalStart(null, end));
    }
}
