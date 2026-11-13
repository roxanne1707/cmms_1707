package com.grash.event;

import java.util.UUID;

/** Broker messages contain references; sensitive event content stays in PostgreSQL. */
public record DispatchMessage(int schemaVersion, UUID eventId, long companyId, String handler,
                              int replayGeneration, int businessAttempt) {
    public static DispatchMessage original(EventEnvelope event) {
        return new DispatchMessage(1, event.eventId(), event.companyId(), EventEnvelope.HANDLER, 0, 0);
    }
    public String publishKey(String purpose) {
        return companyId + ":" + eventId + ":" + handler + ":" + replayGeneration + ":" + businessAttempt + ":" + purpose;
    }
    public void validate() {
        if (schemaVersion != 1 || eventId == null || companyId <= 0 || !EventEnvelope.HANDLER.equals(handler)
                || replayGeneration < 0 || businessAttempt < 0) throw new IllegalArgumentException("Unsupported dispatch envelope");
    }
}
