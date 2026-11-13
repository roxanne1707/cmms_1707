package com.grash.event;

import java.util.List;

public record DeliveryPayload(EventEnvelope event,EventEnvelope.Target target,EventEnvelope.WebhookTarget webhook) {
    public static DeliveryPayload forTarget(EventEnvelope event,EventEnvelope.Target target) {
        return new DeliveryPayload(header(event),target,null);
    }
    public static DeliveryPayload forWebhook(EventEnvelope event,EventEnvelope.WebhookTarget target) {
        return new DeliveryPayload(header(event),null,target);
    }
    private static EventEnvelope header(EventEnvelope e) {
        return new EventEnvelope(e.schemaVersion(),e.eventId(),e.companyId(),e.eventType(),e.aggregateId(),e.actorId(),e.occurredAt(),List.of(),List.of(),e.facts());
    }
}
