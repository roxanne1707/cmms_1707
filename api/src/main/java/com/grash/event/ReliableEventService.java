package com.grash.event;

import com.grash.dto.license.LicenseEntitlement;
import com.grash.model.*;
import com.grash.mapper.WorkOrderMapper;
import com.grash.model.enums.*;
import com.grash.model.enums.webhook.*;
import com.grash.repository.UserRepository;
import com.grash.repository.RequestRepository;
import com.grash.repository.WorkOrderRepository;
import com.grash.exception.CustomException;
import org.springframework.http.HttpStatus;
import com.grash.repository.WebhookEndpointRepository;
import com.grash.service.LicenseService;
import com.grash.service.MailService;
import com.grash.utils.Helper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class ReliableEventService {
    private final ReliableEventProperties properties;
    private final EventStore store;
    private final WorkOrderMapper workOrderMapper;
    private final MessageSource messages;
    private final UserRepository users;
    private final WebhookEndpointRepository endpoints;
    private final LicenseService licenseService;
    private final ReportAttachmentStore attachments;
    private final RequestRepository requests;
    private final WorkOrderRepository workOrders;
    public ReliableEventService(ReliableEventProperties properties,EventStore store,WorkOrderMapper workOrderMapper,
                                @Qualifier("messageSource") MessageSource messages,UserRepository users,
                                WebhookEndpointRepository endpoints,LicenseService licenseService,ReportAttachmentStore attachments,RequestRepository requests,WorkOrderRepository workOrders) {
        this.properties=properties; this.store=store; this.workOrderMapper=workOrderMapper; this.messages=messages;
        this.users=users; this.endpoints=endpoints; this.licenseService=licenseService;
        this.attachments=attachments; this.requests=requests; this.workOrders=workOrders;
    }
    @Value("${frontend.url}") private String frontendUrl;
    public boolean isEnabled() { return properties.isEnabled(); }

    @Transactional(propagation=Propagation.MANDATORY)
    public void workOrderCreated(WorkOrder order, Long actorId) {
        if(!isEnabled()) return;
        Locale locale=Helper.getLocale(order.getCompany());
        List<EventEnvelope.Target> targets=assignmentTargets(order,order.getUsers(),"ASSIGNMENT",locale);
        publish(order.getCompany(),"WORK_ORDER_CREATED",order.getId(),actorId,targets,
                webhookTargets(order.getCompany(),Set.of(WebhookEvent.NEW_WORK_ORDER),null,order),facts(order),UUID.randomUUID());
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void workOrderAssigned(WorkOrder order,List<Long> newRecipientIds,Long actorId) {
        if(!isEnabled() || newRecipientIds.isEmpty()) return;
        Collection<User> recipients=order.getUsers().stream().filter(u->newRecipientIds.contains(u.getId())).toList();
        publish(order.getCompany(),"WORK_ORDER_ASSIGNED",order.getId(),actorId,
                assignmentTargets(order,recipients,"ASSIGNMENT",Helper.getLocale(order.getCompany())),List.of(),facts(order),UUID.randomUUID());
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void workOrderPatched(WorkOrder order,Collection<WOField> changedFields,Long actorId,Long previousCategoryId) {
        if(!isEnabled() || changedFields==null || changedFields.isEmpty()) return;
        Set<WebhookEvent> events=new HashSet<>(Set.of(WebhookEvent.WORK_ORDER_CHANGE));
        // Category filters are evaluated against the event-time entity below.
        if(changedFields.stream().anyMatch(f->f.name().equalsIgnoreCase("CATEGORY"))) events.add(WebhookEvent.NEW_CATEGORY_ON_WORK_ORDER);
        Map<String,Object> facts=facts(order);
        if(events.contains(WebhookEvent.NEW_CATEGORY_ON_WORK_ORDER)) {
            facts.put("previousCategoryId",previousCategoryId);
            facts.put("newCategoryId",order.getCategory()==null?null:order.getCategory().getId());
            facts.put("newCategoryName",order.getCategory()==null?null:order.getCategory().getName());
        }
        publish(order.getCompany(),"WORK_ORDER_CHANGED",order.getId(),actorId,List.of(),
                webhookTargets(order.getCompany(),events,changedFields,order),facts,UUID.randomUUID());
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void workOrderStatusChanged(WorkOrder order,Status previousStatus,Long actorId) {
        if(!isEnabled() || previousStatus==order.getStatus()) return;
        Locale locale=Helper.getLocale(order.getCompany());
        List<EventEnvelope.Target> targets=new ArrayList<>();
        String actor=actorId==null?"":users.findByIdAndCompany_Id(actorId,order.getCompany().getId()).map(User::getFullName).orElse("");
        if(order.getStatus()==Status.COMPLETE) {
            users.findByCompany_Id(order.getCompany().getId()).stream()
                    .filter(u->u.isEnabled() && u.getRole().getViewPermissions().contains(PermissionEntity.SETTINGS)
                            && u.getUserSettings().shouldEmailUpdatesForWorkOrders())
                    .forEach(u->targets.add(new EventEnvelope.Target(u.getId(),u.getEmail(),"COMPLETION",
                            text("complete_work_order_content",new Object[]{order.getTitle(),actor},Helper.getLocale(u)),
                            text("complete_work_order",null,locale),"WORK_ORDER",order.getId(),null,
                            Helper.getLocale(u).toLanguageTag(),Map.of(),false,true)));
        }
        Map<Long,Request> associated=new LinkedHashMap<>();
        requests.findByCompany_IdAndWorkOrder_Id(order.getCompany().getId(),order.getId()).forEach(r->associated.put(r.getId(),r));
        if(order.getParentRequest()!=null) associated.putIfAbsent(order.getParentRequest().getId(),order.getParentRequest());
        Map<String,Object> facts=facts(order);
        facts.put("previousStatus",previousStatus.name());
        for(Request request:associated.values()) if(order.getCompany().getCompanySettings().getGeneralPreferences().isWoUpdateForRequesters()) {
            String message=text("notification_wo_request",new Object[]{order.getTitle(),text(order.getStatus().name(),null,locale)},locale);
            User requester=request.getCreatedBy()==null?null:users.findByIdAndCompany_Id(request.getCreatedBy(),order.getCompany().getId()).orElse(null);
            String email=requester==null?request.getContact():requester.getEmail();
            if(requester!=null || (email!=null && Helper.isValidEmailAddress(email))) {
                facts.put("requestId",request.getId());
                boolean allowed=!MailService.skipMail.get() && (requester==null || (requester.isEnabled() && requester.getUserSettings().shouldEmailUpdatesForRequests()));
                targets.add(new EventEnvelope.Target(requester==null?null:requester.getId(),email,"REQUEST_STATUS",message,
                        text("request_update",null,locale),"WORK_ORDER",order.getId(),"requester-update.html",locale.toLanguageTag(),
                        Map.of("workOrderLink",frontendUrl+"/app/work-orders/"+order.getId(),"message",message,"requestId",request.getId()),allowed,false));
            }
        }
        publish(order.getCompany(),"WORK_ORDER_STATUS_CHANGED",order.getId(),actorId,targets,
                webhookTargets(order.getCompany(),Set.of(WebhookEvent.WORK_ORDER_CHANGE,WebhookEvent.WORK_ORDER_STATUS_CHANGE),List.of(WOField.STATUS),order),facts,UUID.randomUUID());
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void meterEpisodeOpened(Meter meter,WorkOrderMeterTrigger trigger,double reading,WorkOrder order,Long actorId,String episodeKey) {
        if(!isEnabled()) return;
        Locale locale=Helper.getLocale(meter.getCompany());
        String key=trigger.getTriggerCondition()==WorkOrderMeterTriggerCondition.LESS_THAN?"notification_reading_less_than":"notification_reading_more_than";
        String message=text(key,new Object[]{meter.getName(),trigger.getValue(),meter.getUnit()},locale);
        List<EventEnvelope.Target> targets=meter.getUsers().stream().map(u->new EventEnvelope.Target(u.getId(),u.getEmail(),"METER_ABNORMAL",message,
                text("new_wo",null,locale),"METER",meter.getId(),null,locale.toLanguageTag(),Map.of(),false,true)).toList();
        Map<String,Object> facts=new LinkedHashMap<>();
        facts.put("meterId",meter.getId()); facts.put("meterName",meter.getName()); facts.put("meterTriggerId",trigger.getId());
        facts.put("readingValue",reading); facts.put("triggerValue",trigger.getValue()); facts.put("triggerCondition",trigger.getTriggerCondition().name());
        facts.put("workOrderId",order.getId()); facts.put("episodeKey",episodeKey);
        facts.put("meterTriggerName",trigger.getName()); facts.put("serializedWorkOrder",workOrderMapper.toShowDto(order));
        UUID eventId=UUID.nameUUIDFromBytes((meter.getCompany().getId()+":meter-episode:"+episodeKey).getBytes(StandardCharsets.UTF_8));
        publish(meter.getCompany(),"METER_EPISODE_OPENED",meter.getId(),actorId,targets,
                webhookTargets(meter.getCompany(),Set.of(WebhookEvent.METER_TRIGGER_STATUS_CHANGE),null,null),facts,eventId);
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void workOrderDeleted(WorkOrder order,Long actorId) {
        if(!isEnabled()) return;
        Locale locale=Helper.getLocale(order.getCompany());
        String actor=actorId==null?"":users.findByIdAndCompany_Id(actorId,order.getCompany().getId()).map(User::getFullName).orElse("");
        String title=text("deleted_wo",null,locale);
        List<EventEnvelope.Target> targets=users.findByCompany_Id(order.getCompany().getId()).stream()
                .filter(u->u.isEnabled() && u.getRole().getViewPermissions().contains(PermissionEntity.SETTINGS))
                .map(u->new EventEnvelope.Target(u.getId(),u.getEmail(),"WORK_ORDER_DELETED",title+": "+order.getTitle(),title,"INFO",order.getId(),
                        "deleted-work-order.html",Helper.getLocale(u).toLanguageTag(),
                        Map.of("workOrdersLink",frontendUrl+"/app/work-orders","workOrderTitle",order.getTitle(),"deleter",actor),
                        !MailService.skipMail.get() && u.getUserSettings().isEmailNotified(),false)).toList();
        publish(order.getCompany(),"WORK_ORDER_DELETED",order.getId(),actorId,targets,
                webhookTargets(order.getCompany(),Set.of(WebhookEvent.WORK_ORDER_DELETE),null,order),facts(order),UUID.randomUUID());
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void partQuantityChanged(Part part,double previous,double next,WorkOrder order,Object serializedPart) {
        if(!isEnabled() || previous==next) return;
        Locale locale=Helper.getLocale(part.getCompany());
        List<EventEnvelope.Target> targets=new ArrayList<>();
        // Preserve the existing low-stock alert rule; command idempotency suppresses retried mutations.
        if(next<previous && next<part.getMinQuantity() && licenseService.hasEntitlement(LicenseEntitlement.LOW_STOCK_ALERTS)) {
            Map<Long,User> recipients=new LinkedHashMap<>();
            users.findByCompany_Id(part.getCompany().getId()).stream()
                    .filter(u->u.getRole().getViewPermissions().contains(PermissionEntity.SETTINGS)).forEach(u->recipients.put(u.getId(),u));
            part.getUsers().forEach(u->recipients.put(u.getId(),u));
            String message=text("notification_part_low",new Object[]{part.getName()},locale);
            recipients.values().stream().filter(User::isEnabled).forEach(u->targets.add(new EventEnvelope.Target(u.getId(),u.getEmail(),"LOW_STOCK",message,
                    text("low_stock",null,locale),"PART",part.getId(),"low-stock.html",Helper.getLocale(u).toLanguageTag(),
                    Map.of("partName",part.getName(),"partLink",frontendUrl+"/app/inventory/parts/"+part.getId()),!MailService.skipMail.get(),true)));
        }
        Map<String,Object> facts=new LinkedHashMap<>();facts.put("partId",part.getId());facts.put("partName",part.getName());
        facts.put("previousQuantity",previous);facts.put("newQuantity",next);facts.put("changedAmount",next-previous);facts.put("serializedPart",serializedPart);
        if(order!=null) {facts.put("workOrderId",order.getId());facts.put("workOrderTitle",order.getTitle());}
        List<EventEnvelope.WebhookTarget> webhooks=List.of();
        if(licenseService.hasEntitlement(LicenseEntitlement.WEBHOOK) && part.getCompany().getSubscription().getSubscriptionPlan().getFeatures().contains(PlanFeatures.WEBHOOK))
            webhooks=endpoints.findByCompanyIdAndEnabled(part.getCompany().getId(),true).stream()
                    .filter(e->e.getEvent()==WebhookEvent.PART_QUANTITY_CHANGED)
                    .filter(e->e.getPartFields()==null || e.getPartFields().isEmpty() || e.getPartFields().contains(PartField.QUANTITY))
                    .map(e->new EventEnvelope.WebhookTarget(e.getId(),e.getEvent().name(),e.isSerialize(),"changedPart")).toList();
        publish(part.getCompany(),"PART_QUANTITY_CHANGED",part.getId(),null,targets,webhooks,facts,UUID.randomUUID());
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void preventiveReminder(PreventiveMaintenance pm,String occurrenceKey) {
        if(!isEnabled()) return;
        Locale locale=Helper.getLocale(pm.getCompany());
        Map<Long,User> recipients=new LinkedHashMap<>();
        users.findByCompany_Id(pm.getCompany().getId()).stream().filter(u->u.getRole().getViewPermissions().contains(PermissionEntity.SETTINGS)).forEach(u->recipients.put(u.getId(),u));
        pm.getUsers().forEach(u->recipients.put(u.getId(),u));
        List<EventEnvelope.Target> targets=recipients.values().stream().filter(User::isEnabled)
                .filter(u->u.getUserSettings().shouldEmailUpdatesForWorkOrders())
                .map(u->new EventEnvelope.Target(u.getId(),u.getEmail(),"PREVENTIVE_REMINDER",pm.getTitle(),text("coming_wo",null,locale),
                        "INFO",pm.getId(),"coming-work-order.html",Helper.getLocale(u).toLanguageTag(),
                        Map.of("pmLink",frontendUrl+"/app/preventive-maintenances/"+pm.getId(),"pmTitle",pm.getTitle()),!MailService.skipMail.get(),false)).toList();
        UUID eventId=UUID.nameUUIDFromBytes((pm.getCompany().getId()+":pm-reminder:"+occurrenceKey).getBytes(StandardCharsets.UTF_8));
        publish(pm.getCompany(),"PREVENTIVE_REMINDER",pm.getId(),null,targets,List.of(),Map.of("preventiveMaintenanceId",pm.getId()),eventId);
    }

    @Transactional
    public void workOrderReport(WorkOrder snapshot,User actor,List<Customer> customers,String subject,String message,String filename,byte[] pdf) {
        if(!isEnabled()) throw new IllegalStateException("Reliable report delivery is disabled");
        long companyId=snapshot.getCompany().getId();
        User current=users.findByIdAndCompany_Id(actor.getId(),companyId).filter(u->u.isEnabled() && u.isEnabledInSubscription())
                .orElseThrow(()->new CustomException("Access denied",HttpStatus.FORBIDDEN));
        WorkOrder currentOrder=workOrders.findByIdAndCompany_Id(snapshot.getId(),companyId)
                .orElseThrow(()->new CustomException("Work order not found",HttpStatus.NOT_FOUND));
        if(!currentOrder.canBeViewedBy(current)) throw new CustomException("Access denied",HttpStatus.FORBIDDEN);
        UUID eventId=UUID.randomUUID();
        Map<String,Object> variables=Map.of("messageBody",message);
        String locale=Helper.getLocale(current).toLanguageTag();
        List<EventEnvelope.Target> targets=new ArrayList<>();
        Map<String,Long> customerIds=new LinkedHashMap<>();
        for(Customer customer:customers) if(customer.getCompany().getId().equals(companyId) && Helper.isValidEmailAddress(customer.getEmail())) {
            customerIds.putIfAbsent(customer.getEmail(),customer.getId());
        }
        if(customerIds.isEmpty()) throw new CustomException("No contractors with email addresses found",HttpStatus.BAD_REQUEST);
        customerIds.forEach((email,id)->targets.add(new EventEnvelope.Target(null,email,"WORK_ORDER_REPORT",message,subject,"WORK_ORDER",snapshot.getId(),
                "work-order-report-email.html",locale,Map.of("messageBody",message,"customerId",id),!MailService.skipMail.get(),false)));
        if(!customerIds.containsKey(current.getEmail())) targets.add(new EventEnvelope.Target(current.getId(),current.getEmail(),"WORK_ORDER_REPORT",message,subject,
                "WORK_ORDER",snapshot.getId(),"work-order-report-email.html",locale,variables,!MailService.skipMail.get(),false));
        attachments.save(companyId,eventId,filename,pdf);
        publish(snapshot.getCompany(),"WORK_ORDER_REPORT",snapshot.getId(),current.getId(),targets,List.of(),Map.of("workOrderId",snapshot.getId()),eventId);
    }

    private List<EventEnvelope.Target> assignmentTargets(WorkOrder order,Collection<User> recipients,String purpose,Locale locale) {
        Map<Long,User> unique=new LinkedHashMap<>(); recipients.forEach(u->unique.put(u.getId(),u));
        return unique.values().stream().map(u->new EventEnvelope.Target(u.getId(),u.getEmail(),purpose,
                text("notification_wo_assigned",new Object[]{order.getTitle()},locale),text("new_wo",null,locale),"WORK_ORDER",order.getId(),
                "new-work-order.html",Helper.getLocale(u).toLanguageTag(),Map.of("workOrderLink",frontendUrl+"/app/work-orders/"+order.getId(),"workOrderTitle",order.getTitle()),
                !MailService.skipMail.get() && u.isEnabled() && u.getUserSettings().shouldEmailUpdatesForWorkOrders(),true)).toList();
    }
    private List<EventEnvelope.WebhookTarget> webhookTargets(Company company,Set<WebhookEvent> events,Collection<WOField> changed,WorkOrder order) {
        if(!licenseService.hasEntitlement(LicenseEntitlement.WEBHOOK) || !company.getSubscription().getSubscriptionPlan().getFeatures().contains(PlanFeatures.WEBHOOK)) return List.of();
        return endpoints.findByCompanyIdAndEnabled(company.getId(),true).stream().filter(e->events.contains(e.getEvent()))
                .filter(e->changed==null || changed.isEmpty() || e.getWoFields()==null || e.getWoFields().isEmpty() || changed.stream().anyMatch(e.getWoFields()::contains))
                .filter(e->e.getEvent()!=WebhookEvent.WORK_ORDER_STATUS_CHANGE || order==null || e.getWorkOrderStatuses()==null || e.getWorkOrderStatuses().isEmpty() || e.getWorkOrderStatuses().contains(order.getStatus()))
                .filter(e->e.getEvent()!=WebhookEvent.NEW_CATEGORY_ON_WORK_ORDER || order==null || e.getWorkOrderCategories()==null || e.getWorkOrderCategories().isEmpty() ||
                        (order.getCategory()!=null && e.getWorkOrderCategories().stream().anyMatch(c->c.getId().equals(order.getCategory().getId()))))
                .map(e->new EventEnvelope.WebhookTarget(e.getId(),e.getEvent().name(),e.isSerialize(),
                        e.getEvent()==WebhookEvent.NEW_WORK_ORDER?"newWorkOrder":e.getEvent()==WebhookEvent.WORK_ORDER_DELETE?"deleteWorkOrder":e.getEvent()==WebhookEvent.METER_TRIGGER_STATUS_CHANGE?"triggeredWorkOrder":"changedWorkOrder")).toList();
    }
    private Map<String,Object> facts(WorkOrder order) {
        Map<String,Object> result=new LinkedHashMap<>(); result.put("workOrderId",order.getId());
        result.put("workOrderTitle",order.getTitle()); result.put("newStatus",order.getStatus().name());
        result.put("serializedWorkOrder",workOrderMapper.toShowDto(order)); return result;
    }
    private void publish(Company company,String type,long aggregateId,Long actorId,List<EventEnvelope.Target> targets,
                         List<EventEnvelope.WebhookTarget> webhooks,Map<String,Object> facts,UUID eventId) {
        if(targets.size()>1000 || webhooks.size()>1000) throw new IllegalArgumentException("Event recipient batch exceeds 1000");
        store.append(new EventEnvelope(1,eventId,company.getId(),type,aggregateId,actorId,Instant.now(),List.copyOf(targets),List.copyOf(webhooks),Collections.unmodifiableMap(new LinkedHashMap<>(facts))));
    }
    private String text(String key,Object[] args,Locale locale) { return messages.getMessage(key,args,key,locale); }
}
