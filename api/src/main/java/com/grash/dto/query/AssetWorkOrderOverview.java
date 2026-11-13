package com.grash.dto.query;

import java.math.BigDecimal;
import java.time.Instant;

public record AssetWorkOrderOverview(
        String metricVersion, long companyId, long assetId, long epoch,
        Instant start, Instant end, boolean includeArchived, boolean includeDemo,
        Instant generatedAt, long total, long complete, BigDecimal completionRate,
        BigDecimal averageCycleSeconds, long validCycleSamples, long invalidCycleSamples) {
    public static final String METRIC_VERSION = "creation-cohort-cycle-v1";
}
