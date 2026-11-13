package com.grash.event;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name="cmms.events.enabled",havingValue="true")
public class EventMetrics implements MeterBinder {
    private final JdbcTemplate jdbc;
    private final com.grash.database.DatabasePlatform database;
    @Override public void bindTo(MeterRegistry registry) {
        gauge(registry,"cmms.events.outbox.pending","SELECT count(*) FROM domain_event_outbox WHERE status<>'PUBLISHED'");
        gauge(registry,"cmms.events.outbox.oldest.seconds",database.sql("SELECT coalesce(extract(epoch FROM CURRENT_TIMESTAMP(6)-min(created_at)),0) FROM domain_event_outbox WHERE status<>'PUBLISHED'", "SELECT coalesce(TIMESTAMPDIFF(MICROSECOND,min(created_at),CURRENT_TIMESTAMP(6))/1000000.0,0) FROM domain_event_outbox WHERE status<>'PUBLISHED'"));
        gauge(registry,"cmms.events.consumer.dead","""
                SELECT count(*) FROM event_retry r WHERE r.state='DEAD'
                AND NOT EXISTS (SELECT 1 FROM event_retry newer
                    WHERE newer.company_id=r.company_id AND newer.event_id=r.event_id AND newer.handler=r.handler
                    AND (newer.replay_generation,newer.business_attempt)>(r.replay_generation,r.business_attempt))
                AND NOT EXISTS (SELECT 1 FROM consumer_receipt receipt
                    WHERE receipt.company_id=r.company_id AND receipt.event_id=r.event_id AND receipt.handler=r.handler)
                """);
        gauge(registry,"cmms.events.delivery.dead","SELECT count(*) FROM event_delivery WHERE state='DEAD'");
        gauge(registry,"cmms.events.delivery.pending","SELECT count(*) FROM event_delivery WHERE state IN ('READY','PROCESSING','RETRY')");
        gauge(registry,"cmms.events.quarantine.messages","SELECT count(*) FROM event_quarantine");
    }
    private void gauge(MeterRegistry registry,String name,String sql) {
        Gauge.builder(name,()->{try { return jdbc.queryForObject(sql,Double.class); } catch(RuntimeException unavailable) { return Double.NaN; }})
                .description("Durable reliable event state; no user/event labels").register(registry);
    }
}
