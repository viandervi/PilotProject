package com.creatorrx.recommendation;

import jakarta.inject.Singleton;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.creatorrx.recommendation.Recommendation.Decision.*;

/**
 * Explainable, deterministic v1. Missing observations never imply poor performance.
 * Scores rank opportunities, not predicted view gains.
 */
@Singleton
public class RecommendationEngine {
    private static final int MIN_DAYS = 7;
    private static final long MIN_RECENT_VIEWS = 100;
    private static final double LOW_RETENTION_RATIO = 0.75;
    private static final double REVIVAL_GROWTH = 1.5;

    public Recommendation evaluate(VideoSignal signal, LocalDate today) {
        if (signal.observedDays() < MIN_DAYS || signal.recentViews() < MIN_RECENT_VIEWS)
            return new Recommendation(signal.videoId(), INSUFFICIENT_DATA, 0, 0,
                List.of(signal.observedDays() < MIN_DAYS ? "Only " + signal.observedDays() + " observed days; at least " + MIN_DAYS + " required" : "Only " + signal.recentViews() + " recent views; at least " + MIN_RECENT_VIEWS + " required"));

        var reasons = new ArrayList<String>();
        double confidence = Math.min(1, signal.recentViews() / 1000.0)
            * Math.min(1, signal.observedDays() / 28.0);
        boolean lowAverage = ratioBelow(signal.averageViewPercentage(),
            signal.cohortAverageViewPercentage(), LOW_RETENTION_RATIO);
        boolean lowEarly = ratioBelow(signal.earlyRetention(),
            signal.cohortEarlyRetention(), LOW_RETENTION_RATIO);
        if (lowAverage || lowEarly) {
            if (lowAverage) reasons.add("Average viewed percentage is below channel cohort");
            if (lowEarly) reasons.add("Early retention is below channel cohort");
            double impact = Math.min(1, signal.recentViews() / 5000.0);
            double score = 100 * (0.45 * impact + 0.35 * confidence + 0.20 * (lowAverage && lowEarly ? 1 : 0.7));
            return new Recommendation(signal.videoId(), FIX, round(score), round(confidence), reasons);
        }

        boolean older = signal.publishedAt() != null
            && ChronoUnit.DAYS.between(signal.publishedAt(), today) >= 90;
        boolean accelerating = signal.previousViews() != null
            && signal.previousViews() >= MIN_RECENT_VIEWS
            && signal.recentViews() >= Math.ceil(signal.previousViews() * REVIVAL_GROWTH);
        if (older && accelerating) {
            reasons.add("Older video has at least 50% more views than the previous comparable window");
            double score = 100 * (0.50 * Math.min(1, signal.recentViews() / 5000.0)
                + 0.35 * confidence + 0.15);
            return new Recommendation(signal.videoId(), REVIVE, round(score), round(confidence), reasons);
        }

        if (signal.averageViewPercentage() == null || signal.cohortAverageViewPercentage() == null)
            return new Recommendation(signal.videoId(), INSUFFICIENT_DATA, 0, round(confidence),
                List.of("Comparable channel cohort baseline unavailable; need at least 6 videos with analytics in the same age cohort"));

        return new Recommendation(signal.videoId(), IGNORE, 0, round(confidence),
            List.of("No actionable performance anomaly detected"));
    }

    public List<Recommendation> rank(List<VideoSignal> videos, LocalDate today) {
        return videos.stream().map(v -> evaluate(v, today))
            .sorted(Comparator.comparingDouble(Recommendation::score).reversed()
                .thenComparing(Recommendation::videoId)).toList();
    }

    private static boolean ratioBelow(Double observed, Double baseline, double ratio) {
        return observed != null && baseline != null && Double.isFinite(observed)
            && Double.isFinite(baseline) && observed >= 0 && baseline > 0
            && observed / baseline < ratio;
    }
    private static double round(double value) { return Math.round(value * 1000) / 1000.0; }
}
