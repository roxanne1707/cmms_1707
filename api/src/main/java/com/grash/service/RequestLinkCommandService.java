package com.grash.service;

import com.grash.event.*;
import com.grash.exception.CustomException;
import com.grash.model.*;
import com.grash.model.enums.*;
import com.grash.repository.UserRepository;
import com.grash.security.FreshMaintenanceActor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Multiple reports about the same equipment share one repair. No extra work order is created. */
@Service
@RequiredArgsConstructor
public class RequestLinkCommandService {
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final FreshMaintenanceActor actors;
    private final EventStore events;
    private final ReliableEventProperties eventProperties;
    private final UserRepository users;

    public record Result(long requestId, long workOrderId, Instant linkedAt, boolean replayed) {}
    public record Page(List<Map<String,Object>> content, long totalElements, int page, int size) {}

    @Transactional
    public Result link(long requestId, long workOrderId, String reason, User identity) {
        User actor=manager(identity);
        if (reason==null || reason.trim().length()<5 || reason.length()>500)
            throw new CustomException("Provide a linking reason of 5 to 500 characters",HttpStatus.BAD_REQUEST);
        Request request=em.find(Request.class,requestId,LockModeType.PESSIMISTIC_WRITE);
        if(request==null) throw new CustomException("Request not found",HttpStatus.NOT_FOUND);
        em.refresh(request);
        requireCompany(request.getCompany(),actor);
        if(!request.canBeViewedBy(actor)) denied();
        WorkOrder order=em.find(WorkOrder.class,workOrderId,LockModeType.PESSIMISTIC_WRITE);
        if(order==null) throw new CustomException("Work order not found",HttpStatus.NOT_FOUND);
        em.refresh(order);
        requireCompany(order.getCompany(),actor);
        if(!order.canBeViewedBy(actor) || !order.canBeEditedBy(actor)) denied();
        // Re-check current permissions before even a natural-idempotent response.
        if(request.getWorkOrder()!=null) {
            if(!Objects.equals(request.getWorkOrder().getId(),workOrderId))
                throw new CustomException("Request is already linked to a different work order",HttpStatus.CONFLICT);
            List<Instant> dates=jdbc.query("SELECT linked_at FROM request_link_audit WHERE request_id=? AND company_id=? AND work_order_id=?",
                    (rs,n)->rs.getTimestamp(1).toInstant(),requestId,actor.getCompany().getId(),workOrderId);
            return new Result(requestId,workOrderId,dates.isEmpty()?null:dates.get(0),true);
        }
        if(request.isCancelled() || order.isArchived() || order.getStatus()==Status.COMPLETE)
            throw new CustomException("Only pending requests may be linked to an active work order",HttpStatus.CONFLICT);
        Long requestAsset=request.getAsset()==null?null:request.getAsset().getId();
        Long orderAsset=order.getAsset()==null?null:order.getAsset().getId();
        if(!Objects.equals(requestAsset,orderAsset))
            throw new CustomException("Request and work order must refer to the same equipment",HttpStatus.CONFLICT);
        request.setWorkOrder(order);
        // parentRequest remains the original source. A duplicate report must not overwrite it.
        em.flush();
        Instant linkedAt=jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(6)",java.sql.Timestamp.class).toInstant();
        jdbc.update("INSERT INTO request_link_audit(request_id,company_id,work_order_id,actor_id,reason,linked_at) VALUES(?,?,?,?,?,?)",
                requestId,actor.getCompany().getId(),workOrderId,actor.getId(),reason.trim(),java.sql.Timestamp.from(linkedAt));
        if(eventProperties.isEnabled()) publish(request,order,actor,linkedAt);
        return new Result(requestId,workOrderId,linkedAt,false);
    }

    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page pending(User identity,int page,int size) {
        User actor=manager(identity);validatePage(page,size);long company=actor.getCompany().getId();
        List<Map<String,Object>> rows=jdbc.queryForList("""
                SELECT r.id,r.custom_id AS "customId",r.title,r.asset_id AS "assetId",a.name AS "assetName"
                FROM request r LEFT JOIN asset a ON a.id=r.asset_id AND a.company_id=r.company_id
                WHERE r.company_id=? AND r.work_order_id IS NULL AND NOT r.cancelled
                ORDER BY r.created_at DESC,r.id DESC LIMIT ? OFFSET ?
                """,company,size,(long)page*size);
        long count=jdbc.queryForObject("SELECT count(*) FROM request WHERE company_id=? AND work_order_id IS NULL AND NOT cancelled",Long.class,company);
        return new Page(rows,count,page,size);
    }

    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page candidates(long requestId,User identity,int page,int size) {
        User actor=manager(identity);validatePage(page,size);
        Request request=em.find(Request.class,requestId);
        if(request==null) throw new CustomException("Request not found",HttpStatus.NOT_FOUND);
        requireCompany(request.getCompany(),actor);
        if(!request.canBeViewedBy(actor)) denied();
        Long asset=request.getAsset()==null?null:request.getAsset().getId();long company=actor.getCompany().getId();
        String predicate="company_id=? AND asset_id IS NOT DISTINCT FROM ? AND NOT archived AND status<>?";
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,custom_id AS \"customId\",title,status,priority FROM work_order WHERE "+predicate+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                company,asset,Status.COMPLETE.ordinal(),size,(long)page*size);
        long count=jdbc.queryForObject("SELECT count(*) FROM work_order WHERE "+predicate,Long.class,company,asset,Status.COMPLETE.ordinal());
        return new Page(rows,count,page,size);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> audit(long requestId,User identity) {
        User actor=manager(identity);
        return jdbc.queryForList("SELECT request_id AS \"requestId\",work_order_id AS \"workOrderId\",actor_id AS \"actorId\",reason,linked_at AS \"linkedAt\" FROM request_link_audit WHERE request_id=? AND company_id=? ORDER BY linked_at,id",
                requestId,actor.getCompany().getId());
    }

    private User manager(User identity) {
        User actor=actors.load(identity);
        if(!(actor.getRole().getViewPermissions().contains(PermissionEntity.SETTINGS) || actor.getRole().getCode()==RoleCode.LIMITED_ADMIN)
                || !actor.getRole().getViewPermissions().containsAll(Set.of(PermissionEntity.REQUESTS,PermissionEntity.WORK_ORDERS))
                || !actor.getRole().getViewOtherPermissions().containsAll(Set.of(PermissionEntity.REQUESTS,PermissionEntity.WORK_ORDERS))) denied();
        return actor;
    }
    private static void requireCompany(Company company,User actor) {
        if(company==null || !Objects.equals(company.getId(),actor.getCompany().getId())) denied();
    }
    private static void validatePage(int page,int size) {
        if(page<0 || page>100000 || size<1 || size>100) throw new CustomException("Invalid page",HttpStatus.BAD_REQUEST);
    }
    private static void denied(){throw new CustomException("Access denied",HttpStatus.FORBIDDEN);}
    private void publish(Request request,WorkOrder order,User actor,Instant now) {
        User requester=request.getCreatedBy()==null?null:users.findByIdAndCompany_Id(request.getCreatedBy(),actor.getCompany().getId()).orElse(null);
        List<EventEnvelope.Target> targets=requester==null?List.of():List.of(new EventEnvelope.Target(requester.getId(),requester.getEmail(),"REQUEST_STATUS",
                "报修「"+request.getTitle()+"」已关联维修工单 "+order.getCustomId(),"报修已关联工单","WORK_ORDER",order.getId(),null,"zh-CN",
                Map.of("requestId",request.getId()),false,false));
        UUID id=UUID.nameUUIDFromBytes((actor.getCompany().getId()+":request-linked:"+request.getId()+":"+order.getId()).getBytes(StandardCharsets.UTF_8));
        events.append(new EventEnvelope(1,id,actor.getCompany().getId(),"REQUEST_LINKED",order.getId(),actor.getId(),now,targets,List.of(),
                Map.of("requestId",request.getId(),"workOrderId",order.getId())));
    }
}
