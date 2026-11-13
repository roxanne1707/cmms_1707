package com.grash.service;

import com.grash.model.enums.MeterEpisodePolicy;
import com.grash.model.enums.WorkOrderMeterTriggerCondition;
import java.time.Instant;

/** Pure policy, separated from persistence so threshold and recovery behavior can be exhaustively checked. */
public final class MeterEpisodeRules {
    private MeterEpisodeRules() {}
    public static boolean abnormal(double value, int threshold, WorkOrderMeterTriggerCondition condition) {
        if (!Double.isFinite(value) || condition == null) throw new IllegalArgumentException("A finite reading and condition are required");
        return condition == WorkOrderMeterTriggerCondition.LESS_THAN ? value < threshold : value > threshold;
    }
    public static boolean shouldOpen(boolean abnormal, boolean openedInEpisode, long openedCount, MeterEpisodePolicy policy,
                                     Instant firstAbnormal, Instant now, int waitSeconds) {
        return abnormal && !openedInEpisode && !(policy == MeterEpisodePolicy.ONCE && openedCount > 0)
                && !now.isBefore(firstAbnormal.plusSeconds(Math.max(0, waitSeconds)));
    }
}
