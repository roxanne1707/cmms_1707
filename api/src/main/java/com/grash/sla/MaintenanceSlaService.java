package com.grash.sla;

import com.grash.event.*;
import com.grash.exception.CustomException;
import com.grash.model.*;
import com.grash.model.enums.*;
import com.grash.repository.*;
import com.grash.security.FreshMaintenanceActor;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MaintenanceSlaService {
    private final JdbcTemplate jdbc;
    private final com.grash.database.DatabasePlatform database;
    private final FreshMaintenanceActor actors;
    private final WorkOrderRepository workOrders;
    private final UserRepository users;
    private final EventStore events;
    private final ReliableEventProperties eventProperties;
    private final EntityManager em;
    @Value("${cmms.sla.enabled:false}") private boolean enabled;
    public record Policy(Priority priority,int responseMinutes,int repairMinutes,boolean enabled,Instant effectiveFrom) {}
    public record CaseRow(long workOrderId,String customId,String title,Priority priority,Instant startedAt,
                          Instant responseDueAt,Instant repairDueAt,Instant respondedAt,Instant completedAt,
                          boolean responseBreached,boolean repairBreached,boolean responseNotified,boolean repairNotified) {}
    public record Board(List<CaseRow> content,long totalElements,int page,int size,long responseBreached,long repairBreached,long completed) {}
    private record Due(long workOrderId,long companyId,Instant responseDue,Instant repairDue,Instant responded,
                       Instant completed,UUID responseEvent,UUID repairEvent) {}

    @Transactional(readOnly=true)
    public List<Policy> policies(User identity) {
        User actor=manager(identity);
        return jdbc.query("SELECT * FROM maintenance_sla_policy WHERE company_id=? ORDER BY priority",
                (rs,n)->new Policy(Priority.values()[rs.getInt("priority")],rs.getInt("response_minutes"),rs.getInt("repair_minutes"),rs.getBoolean("enabled"),rs.getTimestamp("effective_from").toInstant()),actor.getCompany().getId());
    }

    @Transactional
    public Policy savePolicy(Priority priority,int response,int repair,boolean active,User identity) {
        User actor=manager(identity);
        if(priority==null || response<1 || repair<response || repair>525600)
            throw new CustomException("Response and repair minutes must be 1..525600, repair >= response",HttpStatus.BAD_REQUEST);
        jdbc.update(database.sql("""
          INSERT INTO maintenance_sla_policy(company_id,priority,response_minutes,repair_minutes,enabled,updated_by)
          VALUES(?,?,?,?,?,?) ON CONFLICT(company_id,priority) DO UPDATE SET
           response_minutes=EXCLUDED.response_minutes,repair_minutes=EXCLUDED.repair_minutes,enabled=EXCLUDED.enabled,
           effective_from=CASE WHEN maintenance_sla_policy.enabled AND EXCLUDED.enabled THEN maintenance_sla_policy.effective_from ELSE CURRENT_TIMESTAMP(6) END,
           updated_by=EXCLUDED.updated_by,updated_at=CURRENT_TIMESTAMP(6)
          ""","""
          INSERT INTO maintenance_sla_policy(company_id,priority,response_minutes,repair_minutes,enabled,updated_by)
          VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE
           response_minutes=VALUES(response_minutes),repair_minutes=VALUES(repair_minutes),
           effective_from=CASE WHEN maintenance_sla_policy.enabled AND VALUES(enabled) THEN maintenance_sla_policy.effective_from ELSE CURRENT_TIMESTAMP(6) END,
           enabled=VALUES(enabled),updated_by=VALUES(updated_by),updated_at=CURRENT_TIMESTAMP(6)
          """),actor.getCompany().getId(),priority.ordinal(),response,repair,active,actor.getId());
        return policies(actor).stream().filter(p->p.priority()==priority).findFirst().orElseThrow();
    }

    @Transactional
    public void enroll(long orderId,String reason,User identity) {
        User actor=manager(identity);
        if(reason==null || reason.trim().length()<5 || reason.length()>500) throw new CustomException("Provide an enrollment reason of 5 to 500 characters",HttpStatus.BAD_REQUEST);
        List<Long> locked=jdbc.query("SELECT id FROM work_order WHERE id=? AND company_id=? FOR UPDATE",(rs,n)->rs.getLong(1),orderId,actor.getCompany().getId());
        if(locked.isEmpty()) throw new CustomException("Work order not found",HttpStatus.NOT_FOUND);
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM maintenance_sla_case WHERE work_order_id=? AND company_id=?)",Boolean.class,orderId,actor.getCompany().getId()))) return;
        int changed=jdbc.update(database.sql("""
          INSERT INTO maintenance_sla_case(work_order_id,company_id,priority,started_at,response_due_at,repair_due_at,responded_at,enrolled_by,enrollment_reason)
          SELECT w.id,w.company_id,w.priority,w.created_at AT TIME ZONE 'UTC',
           (w.created_at AT TIME ZONE 'UTC')+make_interval(mins=>p.response_minutes),
           (w.created_at AT TIME ZONE 'UTC')+make_interval(mins=>p.repair_minutes),
           CASE WHEN w.first_time_to_react>=w.created_at THEN w.first_time_to_react AT TIME ZONE 'UTC' ELSE NULL END,?,?
          FROM work_order w JOIN maintenance_sla_policy p ON p.company_id=w.company_id AND p.priority=w.priority
          WHERE w.id=? AND w.company_id=? AND p.enabled AND NOT w.archived AND NOT w.is_demo AND w.status<>?
          ON CONFLICT(work_order_id) DO NOTHING
          ""","""
          INSERT INTO maintenance_sla_case(work_order_id,company_id,priority,started_at,response_due_at,repair_due_at,responded_at,enrolled_by,enrollment_reason)
          SELECT w.id,w.company_id,w.priority,w.created_at,
           TIMESTAMPADD(MINUTE,p.response_minutes,w.created_at),
           TIMESTAMPADD(MINUTE,p.repair_minutes,w.created_at),
           CASE WHEN w.first_time_to_react>=w.created_at THEN w.first_time_to_react ELSE NULL END,?,?
          FROM work_order w JOIN maintenance_sla_policy p ON p.company_id=w.company_id AND p.priority=w.priority
          WHERE w.id=? AND w.company_id=? AND p.enabled AND NOT w.archived AND NOT w.is_demo AND w.status<>?
          ON DUPLICATE KEY UPDATE work_order_id=work_order_id
          """),actor.getId(),reason.trim(),orderId,actor.getCompany().getId(),Status.COMPLETE.ordinal());
        if(changed==0) throw new CustomException("An enabled priority policy and an active non-demo work order are required",HttpStatus.CONFLICT);
    }

    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Board board(User identity,int page,int size) {
        User actor=manager(identity);
        if(page<0 || page>100000 || size<1 || size>100) throw new CustomException("Invalid page",HttpStatus.BAD_REQUEST);
        long company=actor.getCompany().getId();Instant now=now();
        List<CaseRow> rows=jdbc.query("""
           SELECT c.*,w.custom_id,w.title FROM maintenance_sla_case c JOIN work_order w ON w.id=c.work_order_id AND w.company_id=c.company_id
           WHERE c.company_id=? ORDER BY c.repair_due_at DESC,c.work_order_id DESC LIMIT ? OFFSET ?
           """,(rs,n)->{Instant responded=instant(rs.getTimestamp("responded_at")),completed=instant(rs.getTimestamp("completed_at"));
            Instant responseDue=instant(rs.getTimestamp("response_due_at")),repairDue=instant(rs.getTimestamp("repair_due_at"));
            return new CaseRow(rs.getLong("work_order_id"),rs.getString("custom_id"),rs.getString("title"),Priority.values()[rs.getInt("priority")],instant(rs.getTimestamp("started_at")),
             responseDue,repairDue,responded,completed,breached(responseDue,responded,now),breached(repairDue,completed,now),rs.getObject("response_event_id")!=null,rs.getObject("repair_event_id")!=null);
           },company,size,(long)page*size);
        Map<String,Object> summary=jdbc.queryForMap("""
           SELECT count(*) AS total,
            count(CASE WHEN COALESCE(responded_at,?)>response_due_at THEN 1 END) AS response,
            count(CASE WHEN COALESCE(completed_at,?)>repair_due_at THEN 1 END) AS repair,
            count(CASE WHEN completed_at IS NOT NULL THEN 1 END) AS completed
           FROM maintenance_sla_case WHERE company_id=?
           """,Timestamp.from(now),Timestamp.from(now),company);
        return new Board(rows,((Number)summary.get("total")).longValue(),page,size,((Number)summary.get("response")).longValue(),((Number)summary.get("repair")).longValue(),((Number)summary.get("completed")).longValue());
    }

    /** Quartz overlap is harmless: lock order is work_order -> case, identical to the capture trigger. */
    @Transactional
    public int processDue() {
        if(!enabled || !eventProperties.isEnabled()) return 0;
        Instant now=now();
        List<Due> due=jdbc.query(database.sql("""
          SELECT c.* FROM work_order w JOIN maintenance_sla_case c ON c.work_order_id=w.id AND c.company_id=w.company_id
          WHERE NOT w.archived AND ((c.response_event_id IS NULL AND COALESCE(c.responded_at,?)>c.response_due_at)
             OR (c.repair_event_id IS NULL AND COALESCE(c.completed_at,?)>c.repair_due_at))
          ORDER BY c.repair_due_at,c.work_order_id LIMIT 100 FOR UPDATE OF w,c SKIP LOCKED
          ""","""
          SELECT c.* FROM work_order w JOIN maintenance_sla_case c ON c.work_order_id=w.id AND c.company_id=w.company_id
          WHERE NOT w.archived AND ((c.response_event_id IS NULL AND COALESCE(c.responded_at,?)>c.response_due_at)
             OR (c.repair_event_id IS NULL AND COALESCE(c.completed_at,?)>c.repair_due_at))
          ORDER BY c.repair_due_at,c.work_order_id LIMIT 100 FOR UPDATE SKIP LOCKED
          """),(rs,n)->new Due(rs.getLong("work_order_id"),rs.getLong("company_id"),instant(rs.getTimestamp("response_due_at")),instant(rs.getTimestamp("repair_due_at")),
               instant(rs.getTimestamp("responded_at")),instant(rs.getTimestamp("completed_at")),com.grash.database.DatabasePlatform.uuid(rs,"response_event_id"),com.grash.database.DatabasePlatform.uuid(rs,"repair_event_id")),Timestamp.from(now),Timestamp.from(now));
        int emitted=0;
        for(Due row:due) {
            WorkOrder order=workOrders.findByIdAndCompany_Id(row.workOrderId(),row.companyId()).orElseThrow();
            em.refresh(order);
            if(row.responseEvent()==null && breached(row.responseDue(),row.responded(),now)) {emit(order,"RESPONSE",row.responseDue(),now);emitted++;}
            if(row.repairEvent()==null && breached(row.repairDue(),row.completed(),now)) {emit(order,"REPAIR",row.repairDue(),now);emitted++;}
        }
        return emitted;
    }

    private void emit(WorkOrder order,String kind,Instant deadline,Instant now) {
        UUID id=UUID.nameUUIDFromBytes((order.getCompany().getId()+":sla:"+order.getId()+":"+kind).getBytes(StandardCharsets.UTF_8));
        int inserted=jdbc.update(database.sql("INSERT INTO maintenance_sla_escalation(company_id,work_order_id,kind,event_id) VALUES(?,?,?,?) ON CONFLICT(work_order_id,kind) DO NOTHING","INSERT INTO maintenance_sla_escalation(company_id,work_order_id,kind,event_id) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE company_id=company_id"),
                order.getCompany().getId(),order.getId(),kind,id);
        if(inserted>0) {
            Map<Long,User> recipients=new LinkedHashMap<>();
            order.getUsers().forEach(u->recipients.put(u.getId(),u));
            users.findByCompany_Id(order.getCompany().getId()).stream()
                    .filter(u->u.getRole().getViewPermissions().contains(PermissionEntity.SETTINGS) || u.getRole().getCode()==RoleCode.LIMITED_ADMIN)
                    .forEach(u->recipients.put(u.getId(),u));
            String message="维修工单「"+order.getTitle()+"」超过"+(kind.equals("RESPONSE")?"响应":"修复")+"期限，已升级提醒维护管理员。";
            List<EventEnvelope.Target> targets=recipients.values().stream().filter(User::isEnabled)
                    .map(u->new EventEnvelope.Target(u.getId(),u.getEmail(),"SLA_"+kind,message,"维修工单 SLA 超时","WORK_ORDER",order.getId(),null,"zh-CN",Map.of(),false,false)).toList();
            events.append(new EventEnvelope(1,id,order.getCompany().getId(),"SLA_"+kind+"_BREACHED",order.getId(),null,now,targets,List.of(),Map.of("workOrderId",order.getId(),"deadline",deadline.toString(),"kind",kind)));
        }
        String column=kind.equals("RESPONSE")?"response_event_id":"repair_event_id";
        jdbc.update("UPDATE maintenance_sla_case SET "+column+"=? WHERE work_order_id=? AND company_id=?",id,order.getId(),order.getCompany().getId());
    }
    private User manager(User identity) {
        if(!enabled) throw new CustomException("SLA module is disabled",HttpStatus.NOT_FOUND);
        User actor=actors.load(identity);
        if(!(actor.getRole().getViewPermissions().contains(PermissionEntity.SETTINGS) || actor.getRole().getCode()==RoleCode.LIMITED_ADMIN)
                || !actor.getRole().getViewPermissions().contains(PermissionEntity.WORK_ORDERS)
                || !actor.getRole().getViewOtherPermissions().contains(PermissionEntity.WORK_ORDERS))
            throw new CustomException("Access denied",HttpStatus.FORBIDDEN);
        return actor;
    }
    static boolean breached(Instant deadline,Instant achieved,Instant now) {return (achieved==null?now:achieved).isAfter(deadline);}
    private Instant now(){return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(6)",Timestamp.class).toInstant();}
    private static Instant instant(Timestamp value){return value==null?null:value.toInstant();}
}
