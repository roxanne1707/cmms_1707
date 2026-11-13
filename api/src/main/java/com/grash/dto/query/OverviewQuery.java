package com.grash.dto.query;

import com.grash.exception.CustomException;
import lombok.Data;
import org.springframework.http.HttpStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** UTC, millisecond precision, half-open creation cohort. Archived work remains history. */
@Data
public class OverviewQuery {
    private Instant start;
    private Instant end;
    private boolean includeArchived = true;
    private boolean includeDemo = false;

    public void normalizeAndValidate() {
        if (start == null || end == null) bad("start and end are required");
        start = start.truncatedTo(ChronoUnit.MILLIS);
        end = end.truncatedTo(ChronoUnit.MILLIS);
        if (start.isBefore(Instant.parse("1900-01-01T00:00:00Z"))
                || end.isAfter(Instant.parse("2201-01-01T00:00:00Z"))) bad("Dates must be within 1900..2200");
        if (!start.isBefore(end) || Duration.between(start, end).compareTo(Duration.ofDays(366)) > 0)
            bad("Use a non-empty interval of at most 366 days");
    }
    public String getStartUtc() { return start.atOffset(java.time.ZoneOffset.UTC).toLocalDateTime().toString(); }
    public String getEndUtc() { return end.atOffset(java.time.ZoneOffset.UTC).toLocalDateTime().toString(); }
    protected static void bad(String message) { throw new CustomException(message, HttpStatus.BAD_REQUEST); }
}
