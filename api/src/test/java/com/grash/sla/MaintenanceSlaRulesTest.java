package com.grash.sla;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class MaintenanceSlaRulesTest {
    final Instant deadline=Instant.parse("2026-11-14T01:00:00Z");
    @Test void exactDeadlineIsStillCompliant(){assertFalse(MaintenanceSlaService.breached(deadline,null,deadline));}
    @Test void missingResponseBecomesOverdue(){assertTrue(MaintenanceSlaService.breached(deadline,null,deadline.plusMillis(1)));}
    @Test void timelyResponseRemainsCompliantAfterTimePasses(){assertFalse(MaintenanceSlaService.breached(deadline,deadline.minusSeconds(1),deadline.plusSeconds(100)));}
    @Test void lateResponseRemainsABreach(){assertTrue(MaintenanceSlaService.breached(deadline,deadline.plusSeconds(1),deadline.plusSeconds(100)));}
}
