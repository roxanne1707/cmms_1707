package com.grash.service;
import com.grash.model.enums.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
class MeterEpisodeRulesTest {
    @Test void thresholdEqualityIsNormalAndInvalidValuesAreRejected() {
        assertFalse(MeterEpisodeRules.abnormal(10,10,WorkOrderMeterTriggerCondition.MORE_THAN));
        assertFalse(MeterEpisodeRules.abnormal(10,10,WorkOrderMeterTriggerCondition.LESS_THAN));
        assertTrue(MeterEpisodeRules.abnormal(10.1,10,WorkOrderMeterTriggerCondition.MORE_THAN));
        assertTrue(MeterEpisodeRules.abnormal(9.9,10,WorkOrderMeterTriggerCondition.LESS_THAN));
        assertThrows(IllegalArgumentException.class, () -> MeterEpisodeRules.abnormal(Double.NaN,10,WorkOrderMeterTriggerCondition.MORE_THAN));
        assertThrows(IllegalArgumentException.class, () -> MeterEpisodeRules.abnormal(Double.POSITIVE_INFINITY,10,WorkOrderMeterTriggerCondition.MORE_THAN));
    }
    @Test void continuousAbnormalityCannotOpenTwiceAndRecoveryRearms() {
        Instant now=Instant.parse("2026-01-01T00:00:00Z");
        assertTrue(MeterEpisodeRules.shouldOpen(true,false,0,MeterEpisodePolicy.PER_EPISODE,now,now,0));
        assertFalse(MeterEpisodeRules.shouldOpen(true,true,1,MeterEpisodePolicy.PER_EPISODE,now,now,0));
        assertFalse(MeterEpisodeRules.shouldOpen(false,false,1,MeterEpisodePolicy.PER_EPISODE,null,now,0));
        assertTrue(MeterEpisodeRules.shouldOpen(true,false,1,MeterEpisodePolicy.PER_EPISODE,now,now,0));
        assertFalse(MeterEpisodeRules.shouldOpen(true,false,1,MeterEpisodePolicy.ONCE,now,now,0));
    }
    @Test void delayIsSecondsAndBoundaryIsInclusive() {
        Instant first=Instant.parse("2026-01-01T00:00:00Z");
        assertFalse(MeterEpisodeRules.shouldOpen(true,false,0,MeterEpisodePolicy.PER_EPISODE,first,first.plusSeconds(29),30));
        assertTrue(MeterEpisodeRules.shouldOpen(true,false,0,MeterEpisodePolicy.PER_EPISODE,first,first.plusSeconds(30),30));
    }
}
