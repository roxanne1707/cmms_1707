package com.grash.event;

import com.grash.exception.CustomException;
import com.grash.model.User;
import com.grash.model.enums.RoleCode;
import com.grash.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EventReplayService {
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final EventStore store;
    private final EventJson json;

    @Transactional
    public void replayEvent(User caller,UUID eventId,String reason) {
        User user=authorize(caller); long company=user.getCompany().getId(); validateReason(reason);
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",company+":"+eventId);
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM consumer_receipt WHERE company_id=? AND event_id=? AND handler=?)",
                Boolean.class,company,eventId,EventEnvelope.HANDLER))) throw new CustomException("Event already consumed; retry a failed delivery instead",HttpStatus.CONFLICT);
        List<Map<String,Object>> rows=jdbc.queryForList("""
                SELECT envelope,state,replay_generation FROM event_retry WHERE company_id=? AND event_id=? AND handler=?
                ORDER BY replay_generation DESC,business_attempt DESC LIMIT 1 FOR UPDATE
                """,company,eventId,EventEnvelope.HANDLER);
        if(rows.isEmpty()) throw new CustomException("Event not found",HttpStatus.NOT_FOUND);
        Map<String,Object> latest=rows.get(0);
        if(!"DEAD".equals(latest.get("state"))) throw new CustomException("Only a terminal failed event can be replayed",HttpStatus.CONFLICT);
        int generation=((Number)latest.get("replay_generation")).intValue()+1;
        UUID attemptId=UUID.randomUUID();
        DispatchMessage dispatch=new DispatchMessage(1,eventId,company,EventEnvelope.HANDLER,generation,0);
        jdbc.update("""
                INSERT INTO event_retry(id,company_id,event_id,handler,replay_generation,business_attempt,envelope)
                VALUES(?,?,?,?,?,0,?)
                """,attemptId,company,eventId,EventEnvelope.HANDLER,generation,latest.get("envelope"));
        store.publishIntent(attemptId,dispatch,"EVENT",Duration.ZERO);
        audit(user,eventId,"EVENT",reason);
    }
    @Transactional
    public void retryDelivery(User caller,UUID deliveryId,String reason) {
        User user=authorize(caller); validateReason(reason);
        List<String> states=jdbc.query("SELECT state FROM event_delivery WHERE id=? AND company_id=? FOR UPDATE",(rs,n)->rs.getString(1),deliveryId,user.getCompany().getId());
        if(states.isEmpty()) throw new CustomException("Delivery not found",HttpStatus.NOT_FOUND);
        if(!states.get(0).equals("DEAD")) throw new CustomException("Only DEAD deliveries can be retried",HttpStatus.CONFLICT);
        jdbc.update("""
                UPDATE event_delivery SET state='READY',attempt=0,replay_generation=replay_generation+1,
                claim_token=NULL,lease_until=NULL,next_attempt_at=CURRENT_TIMESTAMP,last_error=NULL WHERE id=? AND company_id=?
                """,deliveryId,user.getCompany().getId());
        audit(user,deliveryId,"DELIVERY",reason);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> failures(User caller) {
        User user=authorize(caller); long company=user.getCompany().getId();
        return Map.of("events",jdbc.queryForList("""
                SELECT r.event_id,r.handler,r.replay_generation,r.business_attempt,r.last_error,r.updated_at
                FROM event_retry r WHERE r.company_id=? AND r.state='DEAD'
                AND NOT EXISTS (SELECT 1 FROM event_retry newer
                    WHERE newer.company_id=r.company_id AND newer.event_id=r.event_id AND newer.handler=r.handler
                    AND (newer.replay_generation,newer.business_attempt)>(r.replay_generation,r.business_attempt))
                AND NOT EXISTS (SELECT 1 FROM consumer_receipt receipt
                    WHERE receipt.company_id=r.company_id AND receipt.event_id=r.event_id AND receipt.handler=r.handler)
                ORDER BY r.updated_at DESC,r.event_id LIMIT 100
                """,company),"deliveries",jdbc.queryForList("""
                SELECT id,event_id,channel,purpose,state,attempt,last_error,created_at FROM event_delivery
                WHERE company_id=? AND state IN ('DEAD','SKIPPED') ORDER BY created_at DESC LIMIT 100
                """,company));
    }
    private User authorize(User caller) {
        User user=users.findByIdAndCompany_Id(caller.getId(),caller.getCompany().getId())
                .orElseThrow(()->new CustomException("Access denied",HttpStatus.FORBIDDEN));
        if(!user.isEnabled() || !user.isEnabledInSubscription() || !(user.isOwnsCompany() || user.getRole().getCode()==RoleCode.ADMIN))
            throw new CustomException("Company administrator required",HttpStatus.FORBIDDEN);
        return user;
    }
    private void audit(User user,UUID id,String type,String reason) {
        jdbc.update("INSERT INTO event_replay_audit(id,company_id,actor_id,resource_id,resource_type,reason) VALUES(?,?,?,?,?,?)",
                UUID.randomUUID(),user.getCompany().getId(),user.getId(),id,type,reason.trim());
    }
    private void validateReason(String reason) {
        if(reason==null || reason.trim().length()<5 || reason.length()>500) throw new CustomException("Provide a reason of 5-500 characters",HttpStatus.BAD_REQUEST);
    }
}
