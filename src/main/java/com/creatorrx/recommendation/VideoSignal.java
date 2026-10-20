package com.creatorrx.recommendation;

import java.time.LocalDate;
import java.util.UUID;

/** Null means unavailable, never zero. All averages are channel/cohort-relative. */
public record VideoSignal(UUID videoId, LocalDate publishedAt, long recentViews,
                          Long previousViews, Double averageViewPercentage,
                          Double cohortAverageViewPercentage, Double earlyRetention,
                          Double cohortEarlyRetention, int observedDays) {
    public VideoSignal {
        if (videoId == null || recentViews < 0 || observedDays < 0)
            throw new IllegalArgumentException("Invalid video signals");
        if (previousViews != null && previousViews < 0) throw new IllegalArgumentException("Invalid previous views");
    }
}
