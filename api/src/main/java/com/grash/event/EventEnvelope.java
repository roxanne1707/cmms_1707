package com.grash.event;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Immutable event-time facts. Current authorization is checked again before delivery. */
public record EventEnvelope(int schemaVersion, UUID eventId, long companyId, String eventType,
                            long aggregateId, Long actorId, Instant occurredAt,
                            List<Target> targets, List<WebhookTarget> webhooks, Map<String, Object> facts) {
    public record Target(Long userId, String email, String purpose, String message, String title,
                         String notificationType, long resourceId, String template, String locale,
                         Map<String, Object> variables, boolean emailAllowed, boolean pushAllowed) {}
    public record WebhookTarget(long endpointId, String eventType, boolean serialize, String serializedField) {}
    public static final String HANDLER = "maintenance-notification-v1";
}
