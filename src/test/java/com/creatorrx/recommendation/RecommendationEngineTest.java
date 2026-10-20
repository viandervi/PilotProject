package com.creatorrx.recommendation;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static com.creatorrx.recommendation.Recommendation.Decision.*;

class RecommendationEngineTest {
    private final RecommendationEngine engine = new RecommendationEngine();
    private final LocalDate today = LocalDate.of(2026, 10, 20);

    @Test void flagsRetentionUnderperformanceAsFix() {
        var s = signal(3000, 1000L, 30.0, 60.0, 0.3, 0.7, 28, 20);
        var result = engine.evaluate(s, today);
        assertEquals(FIX, result.decision());
        assertTrue(result.score() > 0);
        assertEquals(2, result.reasons().size());
    }
    @Test void revivesOldAcceleratingVideo() {
        var result = engine.evaluate(signal(1000, 300L, 55.0, 60.0, null, null, 28, 200), today);
        assertEquals(REVIVE, result.decision());
    }
    @Test void healthyVideoIgnored() {
        assertEquals(IGNORE, engine.evaluate(signal(1000, 900L, 60.0, 60.0, null, null, 28, 10), today).decision());
    }
    @Test void missingDataNeverBecomesZeroPerformance() {
        assertEquals(INSUFFICIENT_DATA,
            engine.evaluate(signal(1000, null, null, 60.0, null, null, 28, 10), today).decision());
    }
    @Test void lowSamplesDoNotTriggerFix() {
        assertEquals(INSUFFICIENT_DATA,
            engine.evaluate(signal(10, 5L, 10.0, 60.0, null, null, 3, 10), today).decision());
    }
    @Test void rankingIsDeterministic() {
        var a = signal(2000, 100L, 20.0, 60.0, null, null, 28, 20);
        var b = signal(1000, 900L, 60.0, 60.0, null, null, 28, 20);
        assertEquals(FIX, engine.rank(java.util.List.of(b, a), today).get(0).decision());
    }
    private VideoSignal signal(long views, Long previous, Double avg, Double cohort,
                               Double early, Double cohortEarly, int days, int age) {
        return new VideoSignal(UUID.randomUUID(), today.minusDays(age), views, previous,
            avg, cohort, early, cohortEarly, days);
    }
}
