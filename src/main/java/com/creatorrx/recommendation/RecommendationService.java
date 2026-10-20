package com.creatorrx.recommendation;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Singleton
@Requires(env = "database")
public class RecommendationService {
    private final RecommendationSignalRepository signals;
    private final RecommendationEngine engine;
    public RecommendationService(RecommendationSignalRepository signals, RecommendationEngine engine) {
        this.signals = signals; this.engine = engine;
    }
    public List<Recommendation> forChannel(UUID creator, UUID channel) throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return engine.rank(signals.load(creator, channel, today.minusDays(2)), today);
    }
}
