package com.grash.event;

import com.grash.model.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationCommandService {
    private final EventStore store;
    private final JdbcTemplate jdbc;
    private final EventJson json;
    private final EventAccessPolicy access;
    private final SimpMessageSendingOperations sockets;

    /** Called synchronously through the proxy; returning means the database commit succeeded. */
    @Transactional
    public void consume(EventStore.Attempt attempt) {
        if(!store.lockAttempt(attempt)) throw new IllegalStateException("Stale attempt owner");
        if(!store.insertReceipt(attempt)) { store.completeAttempt(attempt); return; }
        EventEnvelope event=attempt.event();
        for(EventEnvelope.Target target:event.targets()) {
            boolean mayReceive=access.canReceive(event,target);
            boolean excludeActor=!target.purpose().equals("REQUEST_STATUS") && Objects.equals(event.actorId(),target.userId());
            if(target.userId()!=null && !excludeActor && mayReceive) {
                List<Long> ids=jdbc.query("""
                        INSERT INTO notification(id,created_at,updated_at,created_by,updated_by,message,notification_type,resource_id,seen,user_id,event_id,event_company_id,event_purpose)
                        VALUES(nextval('notification_seq'),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,?,?,?,?,false,?,?,?,?)
                        ON CONFLICT(event_company_id,event_id,user_id,event_purpose) WHERE event_id IS NOT NULL DO NOTHING RETURNING id
                        """,(rs,n)->rs.getLong(1),event.actorId(),event.actorId(),truncate(target.message()),
                        NotificationType.valueOf(target.notificationType()).ordinal(),target.resourceId(),target.userId(),event.eventId(),event.companyId(),target.purpose());
                if(!ids.isEmpty()) afterCommitNotice(ids.get(0),target);
            }
            String destination=target.userId()==null?"contact:"+digest(target.email()):"user:"+target.userId();
            if(target.emailAllowed()) addDelivery(event,"EMAIL",destination,target.purpose(),DeliveryPayload.forTarget(event,target));
            if(target.pushAllowed() && target.userId()!=null && !excludeActor) addDelivery(event,"PUSH",destination,target.purpose(),DeliveryPayload.forTarget(event,target));
        }
        for(EventEnvelope.WebhookTarget target:event.webhooks())
            addDelivery(event,"WEBHOOK","endpoint:"+target.endpointId(),target.eventType(),DeliveryPayload.forWebhook(event,target));
        store.completeAttempt(attempt);
    }
    private void addDelivery(EventEnvelope event,String channel,String destination,String purpose,DeliveryPayload payload) {
        jdbc.update("""
                INSERT INTO event_delivery(id,company_id,event_id,channel,destination_id,purpose,payload)
                VALUES(?,?,?,?,?,?,?) ON CONFLICT(company_id,event_id,channel,destination_id,purpose) DO NOTHING
                """,UUID.randomUUID(),event.companyId(),event.eventId(),channel,destination,purpose,json.write(payload));
    }
    private void afterCommitNotice(long id,EventEnvelope.Target target) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { sockets.convertAndSendToUser(target.email(),"/notifications",Map.of("id",id,"message",target.message(),
                        "notificationType",target.notificationType(),"resourceId",target.resourceId(),"seen",false)); }
                catch(RuntimeException ignored) { log.debug("WebSocket notification {} deferred to client refresh",id); }
            }
        });
    }
    private static String truncate(String value) { return value.length()<=255?value:value.substring(0,255); }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
