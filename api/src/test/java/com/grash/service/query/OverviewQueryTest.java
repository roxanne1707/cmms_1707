package com.grash.service.query;

import com.grash.dto.query.*;
import com.grash.exception.CustomException;
import com.grash.model.enums.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class OverviewQueryTest {
    @Test void normalizesQueryAndKeyToTheSameMillisecondPrecision(){
        OverviewQuery q=new OverviewQuery();
        q.setStart(Instant.parse("2026-01-01T00:00:00.123456Z"));q.setEnd(Instant.parse("2026-01-02T00:00:00.999999Z"));
        q.normalizeAndValidate();
        assertEquals(Instant.parse("2026-01-01T00:00:00.123Z"),q.getStart());
        assertEquals("2026-01-01T00:00:00.123",q.getStartUtc());
        assertTrue(q.isIncludeArchived());assertFalse(q.isIncludeDemo());
    }
    @Test void rejectsNullEmptyReversedAndOversizeIntervals(){
        OverviewQuery q=new OverviewQuery();assertThrows(CustomException.class,q::normalizeAndValidate);
        q.setStart(Instant.parse("2026-01-01T00:00:00Z"));q.setEnd(q.getStart());
        assertThrows(CustomException.class,q::normalizeAndValidate);
        q.setEnd(Instant.parse("2028-01-01T00:00:00Z"));assertThrows(CustomException.class,q::normalizeAndValidate);
    }
    @Test void explicitEnumCodesAndPageLimits(){
        LedgerQuery q=new LedgerQuery();q.setStart(Instant.parse("2026-01-01T00:00:00Z"));q.setEnd(Instant.parse("2026-02-01T00:00:00Z"));
        q.setStatus(Status.COMPLETE);q.setPriority(Priority.HIGH);
        assertEquals(3,q.getStatusCode());assertEquals(3,q.getPriorityCode());
        q.setSize(101);assertThrows(CustomException.class,q::normalizeAndValidate);
        q.setSize(10);q.setPage(-1);assertThrows(CustomException.class,q::normalizeAndValidate);
    }
}
