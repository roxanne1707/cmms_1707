package com.grash.event;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EventStore {
    private final JdbcTemplate jdbc;
    private final com.grash.database.DatabasePlatform database;
    private final EventJson json;
    private final ReliableEventProperties properties;
    public record Publication(UUID id, UUID token, String purpose, String dispatch, int attempt) {}
    public record Attempt(UUID id, UUID token, DispatchMessage dispatch, EventEnvelope event) {}
    private record StoredAttempt(UUID id, String state, UUID token, Instant lease, Instant due, String envelope) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(EventEnvelope event) {
        DispatchMessage message = DispatchMessage.original(event);
        UUID attemptId = UUID.randomUUID();
        jdbc.update(database.sql("""
                INSERT INTO event_retry(id,company_id,event_id,handler,replay_generation,business_attempt,envelope)
                VALUES (?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
                ""","""
                INSERT INTO event_retry(id,company_id,event_id,handler,replay_generation,business_attempt,envelope)
                VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id
                """), attemptId, event.companyId(), event.eventId(), message.handler(), 0, 0, json.write(event));
        UUID existing = jdbc.queryForObject("""
                SELECT id FROM event_retry WHERE company_id=? AND event_id=? AND handler=?
                AND replay_generation=0 AND business_attempt=0
                """, UUID.class, event.companyId(), event.eventId(), message.handler());
        publishIntent(existing, message, "EVENT", Duration.ZERO);
    }

    void publishIntent(UUID attemptId, DispatchMessage message, String purpose, Duration delay) {
        jdbc.update(database.sql("""
                INSERT INTO domain_event_outbox(publication_id,publish_key,retry_id,company_id,event_id,purpose,dispatch,next_attempt_at)
                VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP(6) + (? * INTERVAL '1 millisecond')) ON CONFLICT(publish_key) DO NOTHING
                ""","""
                INSERT INTO domain_event_outbox(publication_id,publish_key,retry_id,company_id,event_id,purpose,dispatch,next_attempt_at)
                VALUES (?,?,?,?,?,?,?,TIMESTAMPADD(MICROSECOND,? * 1000,CURRENT_TIMESTAMP(6))) ON DUPLICATE KEY UPDATE publication_id=publication_id
                """), UUID.randomUUID(), message.publishKey(purpose), attemptId, message.companyId(), message.eventId(), purpose,
                json.write(message), delay.toMillis());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Publication> claimPublications() {
        List<Publication> rows = jdbc.query("""
                SELECT publication_id,purpose,dispatch,publish_attempt FROM domain_event_outbox
                WHERE (status='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP(6))
                   OR (status='PROCESSING' AND lease_until<CURRENT_TIMESTAMP(6))
                ORDER BY next_attempt_at,created_at LIMIT ? FOR UPDATE SKIP LOCKED
                """, (rs, n) -> new Publication(com.grash.database.DatabasePlatform.uuid(rs,1), UUID.randomUUID(), rs.getString(2),rs.getString(3),rs.getInt(4)+1), properties.getBatchSize());
        for (Publication row : rows) jdbc.update(database.sql("""
                UPDATE domain_event_outbox SET status='PROCESSING',claim_token=?,publish_attempt=publish_attempt+1,
                lease_until=CURRENT_TIMESTAMP(6) + (? * INTERVAL '1 second') WHERE publication_id=?
                ""","""
                UPDATE domain_event_outbox SET status='PROCESSING',claim_token=?,publish_attempt=publish_attempt+1,
                lease_until=TIMESTAMPADD(MICROSECOND,? * 1000000,CURRENT_TIMESTAMP(6)) WHERE publication_id=?
                """), row.token(), properties.getLeaseSeconds(), row.id());
        return rows;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean publicationResult(Publication row, boolean accepted, String reason) {
        return jdbc.update(database.sql("""
                UPDATE domain_event_outbox SET status=?,last_error=?,claim_token=NULL,lease_until=NULL,
                published_at=CASE WHEN ? THEN CURRENT_TIMESTAMP(6) ELSE NULL END,
                next_attempt_at=CURRENT_TIMESTAMP(6) + (? * INTERVAL '1 millisecond')
                WHERE publication_id=? AND claim_token=? AND status='PROCESSING'
                ""","""
                UPDATE domain_event_outbox SET status=?,last_error=?,claim_token=NULL,lease_until=NULL,
                published_at=CASE WHEN ? THEN CURRENT_TIMESTAMP(6) ELSE NULL END,
                next_attempt_at=TIMESTAMPADD(MICROSECOND,? * 1000,CURRENT_TIMESTAMP(6))
                WHERE publication_id=? AND claim_token=? AND status='PROCESSING'
                """), accepted ? "PUBLISHED" : "PENDING", reason, accepted,
                RetryPolicy.delay(row.attempt()).toMillis(), row.id(), row.token()) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Attempt> claimAttempt(DispatchMessage message) {
        message.validate();
        List<StoredAttempt> rows = jdbc.query("""
                SELECT id,state,claim_token,lease_until,next_attempt_at,envelope FROM event_retry
                WHERE company_id=? AND event_id=? AND handler=? AND replay_generation=? AND business_attempt=? FOR UPDATE
                """, this::mapAttempt, message.companyId(),message.eventId(),message.handler(),message.replayGeneration(),message.businessAttempt());
        if (rows.isEmpty()) throw new IllegalArgumentException("Unknown durable attempt");
        StoredAttempt row=rows.get(0);
        // All non-claimable states have a durable owner/recovery path; no in-memory handoff is relied on.
        if (!row.state().equals("READY") && !row.state().equals("PROCESSING")) return Optional.empty();
        Instant databaseNow = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(6)", java.sql.Timestamp.class).toInstant();
        if (row.due().isAfter(databaseNow) || (row.state().equals("PROCESSING") && row.lease()!=null && row.lease().isAfter(databaseNow)))
            return Optional.empty();
        UUID token=UUID.randomUUID();
        jdbc.update(database.sql("""
                UPDATE event_retry SET state='PROCESSING',claim_token=?,lease_until=CURRENT_TIMESTAMP(6) + (? * INTERVAL '1 second'),
                updated_at=CURRENT_TIMESTAMP(6) WHERE id=?
                ""","""
                UPDATE event_retry SET state='PROCESSING',claim_token=?,lease_until=TIMESTAMPADD(MICROSECOND,? * 1000000,CURRENT_TIMESTAMP(6)),
                updated_at=CURRENT_TIMESTAMP(6) WHERE id=?
                """),token,properties.getLeaseSeconds(),row.id());
        EventEnvelope envelope;
        try {
            envelope=json.read(row.envelope(),EventEnvelope.class);
            if (!Objects.equals(envelope.eventId(),message.eventId()) || envelope.companyId()!=message.companyId() || envelope.schemaVersion()!=1
                    || envelope.targets()==null || envelope.webhooks()==null || envelope.facts()==null || envelope.occurredAt()==null)
                throw new IllegalArgumentException("Stored envelope identity mismatch");
        } catch(IllegalArgumentException invalidStoredEvent) {
            // A valid broker reference to a corrupt/unsupported stored event must not strand READY work after ACK.
            jdbc.update("UPDATE event_retry SET state='DEAD',last_error='INVALID_STORED_EVENT',claim_token=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",row.id());
            publishIntent(row.id(),message,"DEAD",Duration.ZERO);
            return Optional.empty();
        }
        return Optional.of(new Attempt(row.id(),token,message,envelope));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockAttempt(Attempt attempt) {
        List<String> states=jdbc.query("SELECT state FROM event_retry WHERE id=? AND claim_token=? FOR UPDATE",
                (rs,n)->rs.getString(1),attempt.id(),attempt.token());
        return states.size()==1 && states.get(0).equals("PROCESSING");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean insertReceipt(Attempt attempt) {
        return jdbc.update(database.sql("INSERT INTO consumer_receipt(company_id,event_id,handler) VALUES(?,?,?) ON CONFLICT DO NOTHING","INSERT INTO consumer_receipt(company_id,event_id,handler) VALUES(?,?,?) ON DUPLICATE KEY UPDATE company_id=company_id"),
                attempt.event().companyId(),attempt.event().eventId(),attempt.dispatch().handler())==1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void completeAttempt(Attempt attempt) {
        int changed=jdbc.update("""
                UPDATE event_retry SET state='DONE',claim_token=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP(6)
                WHERE id=? AND claim_token=? AND state='PROCESSING'
                """,attempt.id(),attempt.token());
        if(changed!=1) throw new IllegalStateException("Attempt ownership lost; rollback all effects");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failAttempt(Attempt attempt, Throwable error, boolean permanent) {
        if (!lockOwned(attempt)) return; // Another durable owner has already taken over.
        DispatchMessage old=attempt.dispatch();
        boolean dead=permanent || old.businessAttempt()+1>=properties.getMaxAttempts();
        jdbc.update("""
                UPDATE event_retry SET state=?,last_error=?,claim_token=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP(6)
                WHERE id=? AND claim_token=?
                """,dead?"DEAD":"RETRY_TAKEN_OVER",RetryPolicy.error(error),attempt.id(),attempt.token());
        if(dead) { publishIntent(attempt.id(),old,"DEAD",Duration.ZERO); return; }
        Duration delay=RetryPolicy.delay(old.businessAttempt());
        DispatchMessage next=new DispatchMessage(1,old.eventId(),old.companyId(),old.handler(),old.replayGeneration(),old.businessAttempt()+1);
        UUID nextId=UUID.randomUUID();
        jdbc.update(database.sql("""
                INSERT INTO event_retry(id,company_id,event_id,handler,replay_generation,business_attempt,envelope,next_attempt_at)
                VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP(6) + (? * INTERVAL '1 millisecond')) ON CONFLICT DO NOTHING
                ""","""
                INSERT INTO event_retry(id,company_id,event_id,handler,replay_generation,business_attempt,envelope,next_attempt_at)
                VALUES(?,?,?,?,?,?,?,TIMESTAMPADD(MICROSECOND,? * 1000,CURRENT_TIMESTAMP(6))) ON DUPLICATE KEY UPDATE id=id
                """),nextId,old.companyId(),old.eventId(),old.handler(),old.replayGeneration(),next.businessAttempt(),json.write(attempt.event()),delay.toMillis());
        nextId=jdbc.queryForObject("SELECT id FROM event_retry WHERE company_id=? AND event_id=? AND handler=? AND replay_generation=? AND business_attempt=?",
                UUID.class,old.companyId(),old.eventId(),old.handler(),old.replayGeneration(),next.businessAttempt());
        publishIntent(nextId,next,"EVENT",delay);
    }
    private boolean lockOwned(Attempt attempt) {
        List<String> states=jdbc.query("SELECT state FROM event_retry WHERE id=? AND claim_token=? FOR UPDATE",(rs,n)->rs.getString(1),attempt.id(),attempt.token());
        return states.size()==1 && states.get(0).equals("PROCESSING");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recoverExpiredAttempts() {
        List<UUID> ids=jdbc.query("""
                SELECT id FROM event_retry WHERE state='PROCESSING' AND lease_until<CURRENT_TIMESTAMP(6)
                ORDER BY lease_until LIMIT ? FOR UPDATE SKIP LOCKED
                """,(rs,n)->com.grash.database.DatabasePlatform.uuid(rs,1),properties.getBatchSize());
        for(UUID id:ids) {
            jdbc.update("UPDATE event_retry SET state='READY',claim_token=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",id);
            jdbc.update("""
                    UPDATE domain_event_outbox SET status='PENDING',claim_token=NULL,lease_until=NULL,next_attempt_at=CURRENT_TIMESTAMP(6)
                    WHERE retry_id=? AND purpose='EVENT'
                    """,id);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void quarantine(String fingerprint, String reason) {
        jdbc.update(database.sql("""
                INSERT INTO event_quarantine(fingerprint,reason) VALUES(?,?) ON CONFLICT(fingerprint)
                DO UPDATE SET last_seen_at=CURRENT_TIMESTAMP(6),occurrences=event_quarantine.occurrences+1
                ""","""
                INSERT INTO event_quarantine(fingerprint,reason) VALUES(?,?) ON DUPLICATE KEY UPDATE last_seen_at=CURRENT_TIMESTAMP(6),occurrences=event_quarantine.occurrences+1
                """),fingerprint,reason);
    }
    private StoredAttempt mapAttempt(ResultSet rs,int row) throws SQLException {
        return new StoredAttempt(com.grash.database.DatabasePlatform.uuid(rs,1),rs.getString(2),com.grash.database.DatabasePlatform.uuid(rs,3),
                rs.getTimestamp(4)==null?null:rs.getTimestamp(4).toInstant(),rs.getTimestamp(5).toInstant(),rs.getString(6));
    }
}
