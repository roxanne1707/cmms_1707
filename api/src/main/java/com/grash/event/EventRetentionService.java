package com.grash.event;

import com.grash.database.DatabasePlatform;
import com.grash.exception.CustomException;
import com.grash.service.DomainCommandStore;
import io.micrometer.core.instrument.Metrics;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Retires successful transport payloads, NOT domain history, consumer receipts or failed work. */
@Service
public class EventRetentionService {
    private final JdbcTemplate jdbc;
    private final DatabasePlatform database;
    private final EventJson json;
    private final EventRetentionProperties properties;
    private final TransactionTemplate eventTransaction;
    private final AtomicLong lastSuccess=new AtomicLong(),overdueSeconds=new AtomicLong(),candidateSample=new AtomicLong();
    public record Candidate(long companyId,UUID eventId,String handler,Instant completedAt) {}
    public record BatchResult(UUID batchId,boolean dryRun,String status,int candidates,int archived,int skipped,int failed,long retiredPayloadBytes) {}
    private record Retired(boolean changed,long bytes) {}
    public EventRetentionService(JdbcTemplate jdbc,DatabasePlatform database,EventJson json,
                                 EventRetentionProperties properties,PlatformTransactionManager transactions) {
        this.jdbc=jdbc;this.database=database;this.json=json;this.properties=properties;properties.validate();
        eventTransaction=new TransactionTemplate(transactions);eventTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        eventTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        eventTransaction.setTimeout(properties.getTransactionTimeoutSeconds());
    }
    // Eligibility is conservative and uses terminal activity, not just the original creation time.
    // next_attempt_at is updated by EventDeliveryStore.finish for BOTH SENT and SKIPPED.
    private static final String ELIGIBLE="""
        c.handler=? AND c.completed_at<?
        AND NOT EXISTS(SELECT 1 FROM event_archive_summary a WHERE a.company_id=c.company_id AND a.event_id=c.event_id AND a.handler=c.handler)
        AND EXISTS(SELECT 1 FROM event_retry r WHERE r.company_id=c.company_id AND r.event_id=c.event_id AND r.handler=c.handler AND r.state='DONE'
            AND NOT EXISTS(SELECT 1 FROM event_retry n WHERE n.company_id=r.company_id AND n.event_id=r.event_id AND n.handler=r.handler
                AND (n.replay_generation,n.business_attempt)>(r.replay_generation,r.business_attempt)))
        AND NOT EXISTS(SELECT 1 FROM event_retry r WHERE r.company_id=c.company_id AND r.event_id=c.event_id AND r.handler=c.handler
            AND (r.state IN ('READY','PROCESSING') OR r.updated_at>=? OR r.claim_token IS NOT NULL OR r.lease_until IS NOT NULL))
        AND NOT EXISTS(SELECT 1 FROM domain_event_outbox o WHERE o.company_id=c.company_id AND o.event_id=c.event_id
            AND (o.status<>'PUBLISHED' OR o.published_at IS NULL OR o.published_at>=? OR o.claim_token IS NOT NULL OR o.lease_until IS NOT NULL))
        AND NOT EXISTS(SELECT 1 FROM event_delivery d WHERE d.company_id=c.company_id AND d.event_id=c.event_id
            AND (d.state NOT IN ('SENT','SKIPPED') OR d.next_attempt_at>=? OR d.claim_token IS NOT NULL OR d.lease_until IS NOT NULL))
        AND NOT EXISTS(SELECT 1 FROM event_report_attachment a WHERE a.company_id=c.company_id AND a.event_id=c.event_id)
        """;
    private List<Object> eligibilityArguments(Instant cutoff) {
        Timestamp timestamp=Timestamp.from(cutoff);return new ArrayList<>(List.of(EventEnvelope.HANDLER,timestamp,timestamp,timestamp,timestamp));
    }
    List<Candidate> candidates(Long company,Instant cutoff) {
        var args=eligibilityArguments(cutoff);String scope=company==null?"":" AND c.company_id=?";
        if(company!=null)args.add(company);args.add(properties.getBatchSize());
        return jdbc.query("SELECT c.company_id,c.event_id,c.handler,c.completed_at FROM consumer_receipt c WHERE "+ELIGIBLE+scope+
                " ORDER BY c.completed_at,c.company_id,c.event_id LIMIT ?",(rs,n)->new Candidate(rs.getLong(1),DatabasePlatform.uuid(rs,2),rs.getString(3),rs.getTimestamp(4).toInstant()),args.toArray());
    }
    private boolean eligible(Candidate c,Instant cutoff) {
        var args=eligibilityArguments(cutoff);args.add(c.companyId());args.add(c.eventId());
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM consumer_receipt c WHERE "+ELIGIBLE+" AND c.company_id=? AND c.event_id=?)",Boolean.class,args.toArray()));
    }
    private Instant now() { return Objects.requireNonNull(jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(6)",Timestamp.class)).toInstant(); }
    public long lastSuccessEpochSeconds() { return lastSuccess.get(); }
    public long overdueSeconds() { return overdueSeconds.get(); }
    public long candidateSample() { return candidateSample.get(); }

    /** company=null is reserved for the in-process scheduler, never supplied by an HTTP client. */
    public BatchResult run(Long company,Long actor,boolean dryRun,String reason) {
        if(company!=null&&company<=0)throw new IllegalArgumentException("Invalid company scope");
        if(reason==null||reason.isBlank()||reason.trim().length()<5||reason.length()>500)throw new CustomException("Provide a reason of 5-500 characters",HttpStatus.BAD_REQUEST);
        if(!dryRun&&(!properties.isEnabled()||properties.isDryRun()))
            throw new CustomException("Retention execution requires enabled=true and dry-run=false; preview remains available",HttpStatus.CONFLICT);
        Instant clock=now(),cutoff=clock.minus(Duration.ofDays(properties.getRetainDays()));UUID batch=UUID.randomUUID();
        // An abandoned batch is diagnostic only; per-event atomic transactions make later batches resumable.
        jdbc.update("UPDATE event_retention_batch SET status='ABANDONED',finished_at=CURRENT_TIMESTAMP(6),error_code='WORKER_INTERRUPTED' WHERE status='RUNNING' AND started_at<?"+
                (company==null?"":" AND company_id=?"),company==null?new Object[]{Timestamp.from(clock.minusSeconds(3600))}:new Object[]{Timestamp.from(clock.minusSeconds(3600)),company});
        jdbc.update("INSERT INTO event_retention_batch(id,company_id,actor_id,dry_run,reason,status,cutoff_at) VALUES(?,?,?,?,?,'RUNNING',?)",
                batch,company,actor,dryRun,reason.trim(),Timestamp.from(cutoff));
        long start=System.nanoTime(),bytes=0;int selected=0,archived=0,skipped=0,failed=0;String state="COMPLETED",error=null;
        try {
            var candidates=candidates(company,cutoff);selected=candidates.size();
            // A tenant administrator's preview must not overwrite the global scheduler's backlog sample.
            if(company==null) {
                candidateSample.set(selected);
                overdueSeconds.set(candidates.isEmpty()?0:Math.max(0,Duration.between(candidates.get(0).completedAt(),cutoff).toSeconds()));
            }
            if(!dryRun) for(Candidate candidate:candidates) {
                if(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)>=properties.getBudgetMillis()){state="PARTIAL";break;}
                try {
                    Retired retired=eventTransaction.execute(tx->retire(candidate,cutoff));
                    if(retired!=null&&retired.changed()){archived++;bytes+=retired.bytes();}else skipped++;
                } catch(RuntimeException failure) {
                    failed++;error=failure.getClass().getSimpleName();state=archived==0?"FAILED":"PARTIAL";
                    // Stop on database failure, rather than hammering a failing database for the rest of the batch.
                    break;
                }
            }
        } catch(RuntimeException failure) {failed++;state="FAILED";error=failure.getClass().getSimpleName();}
        jdbc.update("""
            UPDATE event_retention_batch SET status=?,finished_at=CURRENT_TIMESTAMP(6),candidates=?,archived=?,skipped=?,failed=?,retired_payload_bytes=?,error_code=?
            WHERE id=? AND status='RUNNING'
            """,state,selected,archived,skipped,failed,bytes,error,batch);
        String mode=dryRun?"preview":"execute",outcome=failed>0?"failed":state.equals("PARTIAL")?"partial":"completed";
        Metrics.counter("cmms.events.retention.runs","mode",mode,"outcome",outcome).increment();
        Metrics.counter("cmms.events.retention.archived").increment(archived);
        Metrics.counter("cmms.events.retention.payload.bytes").increment(bytes);
        Metrics.timer("cmms.events.retention.duration","mode",mode).record(System.nanoTime()-start,TimeUnit.NANOSECONDS);
        if(company==null&&!dryRun&&failed==0&&state.equals("COMPLETED"))lastSuccess.set(clock.getEpochSecond());
        return new BatchResult(batch,dryRun,state,selected,archived,skipped,failed,bytes);
    }
    private Retired retire(Candidate c,Instant cutoff) {
        database.lock("EVENT_REPLAY:"+c.companyId()+":"+c.eventId());
        if(!eligible(c,cutoff))return new Retired(false,0);
        int limit=properties.getMaxRowsPerEvent()+1;
        // Do not SKIP individual rows: silently skipping part of an event group would make deletion unsafe.
        var attempts=jdbc.queryForList("SELECT id FROM event_retry WHERE company_id=? AND event_id=? AND handler=? ORDER BY id LIMIT ? FOR UPDATE",c.companyId(),c.eventId(),c.handler(),limit);
        var publications=jdbc.queryForList("SELECT publication_id FROM domain_event_outbox WHERE company_id=? AND event_id=? ORDER BY publication_id LIMIT ? FOR UPDATE",c.companyId(),c.eventId(),limit);
        var deliveries=jdbc.queryForList("SELECT id FROM event_delivery WHERE company_id=? AND event_id=? ORDER BY id LIMIT ? FOR UPDATE",c.companyId(),c.eventId(),limit);
        if(attempts.size()+publications.size()+deliveries.size()>properties.getMaxRowsPerEvent()||!eligible(c,cutoff))return new Retired(false,0);
        long bytes=payloadBytes(c);
        if(bytes>properties.getMaxPayloadBytesPerEvent())return new Retired(false,0);
        String envelope=jdbc.queryForObject("SELECT envelope FROM event_retry WHERE company_id=? AND event_id=? AND handler=? ORDER BY replay_generation DESC,business_attempt DESC LIMIT 1",String.class,c.companyId(),c.eventId(),c.handler());
        EventEnvelope event=json.read(envelope,EventEnvelope.class);
        if(event.companyId()!=c.companyId()||!event.eventId().equals(c.eventId())||event.schemaVersion()!=1||event.occurredAt()==null
                ||event.eventType()==null||!event.eventType().matches("[A-Z][A-Z0-9_]{0,79}"))throw new IllegalStateException("Invalid archived event identity");
        long retiredBytes=Math.max(0,bytes-2L*deliveries.size()); // '{}' remains in terminal delivery rows.
        jdbc.update("""
            INSERT INTO event_archive_summary(company_id,event_id,handler,event_type,aggregate_id,occurred_at,completed_at,envelope_sha256,attempt_count,publication_count,delivery_count,retired_payload_bytes)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
            """,c.companyId(),c.eventId(),c.handler(),event.eventType(),event.aggregateId(),Timestamp.from(event.occurredAt()),Timestamp.from(c.completedAt()),
                DomainCommandStore.hash(envelope),attempts.size(),publications.size(),deliveries.size(),retiredBytes);
        int removedPublications=jdbc.update("DELETE FROM domain_event_outbox WHERE company_id=? AND event_id=?",c.companyId(),c.eventId());
        int removedAttempts=jdbc.update("DELETE FROM event_retry WHERE company_id=? AND event_id=? AND handler=?",c.companyId(),c.eventId(),c.handler());
        jdbc.update("UPDATE event_delivery SET payload='{}' WHERE company_id=? AND event_id=? AND state IN ('SENT','SKIPPED')",c.companyId(),c.eventId());
        if(removedPublications!=publications.size()||removedAttempts!=attempts.size())throw new IllegalStateException("Event group changed during retention");
        return new Retired(true,retiredBytes);
    }
    private long payloadBytes(Candidate c) {
        return number("SELECT COALESCE(SUM(OCTET_LENGTH(envelope)),0) FROM event_retry WHERE company_id=? AND event_id=? AND handler=?",c.companyId(),c.eventId(),c.handler())
                +number("SELECT COALESCE(SUM(OCTET_LENGTH(dispatch)),0) FROM domain_event_outbox WHERE company_id=? AND event_id=?",c.companyId(),c.eventId())
                +number("SELECT COALESCE(SUM(OCTET_LENGTH(payload)),0) FROM event_delivery WHERE company_id=? AND event_id=?",c.companyId(),c.eventId());
    }
    private long number(String sql,Object... args) {return Objects.requireNonNull(jdbc.queryForObject(sql,Number.class,args)).longValue();}
    public Map<String,Object> status(long company) {
        Instant cutoff=now().minus(Duration.ofDays(properties.getRetainDays()));
        return Map.of("enabled",properties.isEnabled(),"dryRunDefault",properties.isDryRun(),"retainDays",properties.getRetainDays(),
                "candidateSample",candidates(company,cutoff).size(),"sampleLimit",properties.getBatchSize(),"receiptsAreRetained",true,
                "batches",jdbc.queryForList("SELECT id,dry_run,status,started_at,finished_at,candidates,archived,skipped,failed,retired_payload_bytes,error_code FROM event_retention_batch WHERE company_id=? ORDER BY started_at DESC,id LIMIT 20",company),
                "archives",jdbc.queryForList("SELECT event_id,event_type,aggregate_id,occurred_at,completed_at,archived_at,attempt_count,publication_count,delivery_count,retired_payload_bytes FROM event_archive_summary WHERE company_id=? ORDER BY archived_at DESC,event_id LIMIT 20",company));
    }
}
