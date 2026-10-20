package com.creatorrx.recommendation;

import java.util.List;
import java.util.UUID;

public record Recommendation(UUID videoId, Decision decision, double score,
                             double confidence, List<String> reasons) {
    public enum Decision { FIX, REVIVE, IGNORE, INSUFFICIENT_DATA }
    public Recommendation {
        reasons = List.copyOf(reasons);
    }
}
