package com.grash.event;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class EventDeliveryStore {
    private final JdbcTemplate jdbc;
    private final ReliableEventProperties properties;
    public record Delivery(UUID id,UUID token,String channel,String payload,int attempt) {}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Optional<Delivery> claim() {
        List<Delivery> rows=jdbc.query("""
                SELECT id,channel,payload,attempt FROM event_delivery
                WHERE (state IN ('READY','RETRY') AND next_attempt_at<=CURRENT_TIMESTAMP)
                  OR (state='PROCESSING' AND lease_until<CURRENT_TIMESTAMP)
                ORDER BY next_attempt_at,created_at LIMIT 1 FOR UPDATE SKIP LOCKED
                """,(rs,n)->new Delivery(rs.getObject(1,UUID.class),UUID.randomUUID(),rs.getString(2),rs.getString(3),rs.getInt(4)+1));
        if(rows.isEmpty()) return Optional.empty();
        Delivery d=rows.get(0);
        jdbc.update("""
                UPDATE event_delivery SET state='PROCESSING',claim_token=?,attempt=attempt+1,
                lease_until=CURRENT_TIMESTAMP+(? * INTERVAL '1 second') WHERE id=?
                """,d.token(),properties.getLeaseSeconds(),d.id());
        return Optional.of(d);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean finish(Delivery d,String state,String reason) {
        return jdbc.update("""
                UPDATE event_delivery SET state=?,last_error=?,claim_token=NULL,lease_until=NULL,
                sent_at=CASE WHEN ?='SENT' THEN CURRENT_TIMESTAMP ELSE sent_at END,
                next_attempt_at=CURRENT_TIMESTAMP+(? * INTERVAL '1 millisecond')
                WHERE id=? AND claim_token=? AND state='PROCESSING'
                """,state,reason,state,RetryPolicy.delay(d.attempt()-1).toMillis(),d.id(),d.token())==1;
    }
    public String failureState(Delivery delivery) { return delivery.attempt()>=properties.getMaxAttempts()?"DEAD":"RETRY"; }
}
