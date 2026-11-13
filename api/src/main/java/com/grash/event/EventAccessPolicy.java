package com.grash.event;

import com.grash.dto.license.LicenseEntitlement;
import com.grash.model.*;
import com.grash.model.enums.PlanFeatures;
import com.grash.repository.*;
import com.grash.service.LicenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EventAccessPolicy {
    private final UserRepository users;
    private final WorkOrderRepository workOrders;
    private final MeterRepository meters;
    private final WebhookEndpointRepository endpoints;
    private final PushNotificationTokenRepository pushTokens;
    private final LicenseService licenseService;
    private final JdbcTemplate jdbc;
    private final PartRepository parts;
    private final PreventiveMaintenanceRepository preventiveMaintenances;
    public record WebhookDestination(String url,String secret,boolean serialize) {}

    @Transactional(readOnly=true)
    public boolean canReceive(EventEnvelope event,EventEnvelope.Target target) {
        if(target.purpose().equals("WORK_ORDER_REPORT")) return canReceiveReport(event,target);
        if(target.userId()==null) {
            Object requestId=target.variables().getOrDefault("requestId",event.facts().get("requestId"));
            return target.purpose().equals("REQUEST_STATUS") && requestId!=null && Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM request WHERE id=? AND company_id=? AND contact=? AND work_order_id=?)",Boolean.class,
                    Long.valueOf(requestId.toString()),event.companyId(),target.email(),target.resourceId()));
        }
        Optional<User> optional=users.findByIdAndCompany_Id(target.userId(),event.companyId());
        if(optional.isEmpty() || !optional.get().isEnabled() || !optional.get().isEnabledInSubscription()) return false;
        User user=optional.get();
        // Deleted resources cannot be reloaded; only currently authorized tenant administrators receive this snapshot.
        if(target.purpose().equals("WORK_ORDER_DELETED")) return user.getRole().getViewPermissions().contains(com.grash.model.enums.PermissionEntity.SETTINGS);
        if(target.purpose().equals("LOW_STOCK")) {
            return licenseService.hasEntitlement(LicenseEntitlement.LOW_STOCK_ALERTS)
                    && parts.findByIdAndCompany_Id(target.resourceId(),event.companyId()).map(p->p.canBeViewedBy(user)).orElse(false);
        }
        if(target.purpose().equals("PREVENTIVE_REMINDER")) {
            return preventiveMaintenances.findByIdAndCompany_Id(target.resourceId(),event.companyId()).map(p->p.canBeViewedBy(user)).orElse(false);
        }
        if(target.notificationType().equals("METER")) {
            if(!user.getCompany().getSubscription().getSubscriptionPlan().getFeatures().contains(PlanFeatures.METER)) return false;
            return meters.findByIdAndCompany_Id(target.resourceId(),event.companyId()).map(m->m.canBeViewedBy(user)).orElse(false);
        }
        return workOrders.findByIdAndCompany_Id(target.resourceId(),event.companyId()).map(w->w.canBeViewedBy(user)).orElse(false);
    }
    private boolean canReceiveReport(EventEnvelope event,EventEnvelope.Target target) {
        if(event.actorId()==null) return false;
        Optional<User> actor=users.findByIdAndCompany_Id(event.actorId(),event.companyId())
                .filter(u->u.isEnabled() && u.isEnabledInSubscription());
        if(actor.isEmpty() || !workOrders.findByIdAndCompany_Id(target.resourceId(),event.companyId())
                .map(w->w.canBeViewedBy(actor.get())).orElse(false)) return false;
        if(target.userId()!=null) return target.userId().equals(event.actorId()) && target.email().equals(actor.get().getEmail());
        Object customerId=target.variables().get("customerId");
        return customerId!=null && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM customer WHERE id=? AND company_id=? AND email=?)",Boolean.class,
                Long.valueOf(customerId.toString()),event.companyId(),target.email()));
    }
    @Transactional(readOnly=true)
    public boolean canEmail(EventEnvelope event,EventEnvelope.Target target) {
        if(!canReceive(event,target) || !target.emailAllowed()) return false;
        if(target.userId()==null || target.purpose().equals("WORK_ORDER_REPORT")) return true;
        return users.findByIdAndCompany_Id(target.userId(),event.companyId()).filter(u->u.getEmail().equals(target.email()))
                .map(u->target.purpose().equals("WORK_ORDER_DELETED") ? u.getUserSettings().isEmailNotified() : target.purpose().equals("LOW_STOCK") || (target.purpose().equals("REQUEST_STATUS") ? u.getUserSettings().shouldEmailUpdatesForRequests() : u.getUserSettings().shouldEmailUpdatesForWorkOrders())).orElse(false);
    }
    @Transactional(readOnly=true)
    public Optional<String> pushToken(EventEnvelope event,EventEnvelope.Target target) {
        if(target.userId()==null || !canReceive(event,target)) return Optional.empty();
        return pushTokens.findByUser_Id(target.userId()).map(PushNotificationToken::getToken);
    }
    @Transactional(readOnly=true)
    public Optional<WebhookDestination> webhook(EventEnvelope event,EventEnvelope.WebhookTarget target) {
        return endpoints.findByIdAndCompanyId(target.endpointId(),event.companyId()).filter(WebhookEndpoint::isEnabled)
                .filter(e->e.getEvent().name().equals(target.eventType()))
                .filter(e->licenseService.hasEntitlement(LicenseEntitlement.WEBHOOK)
                        && e.getCompany().getSubscription().getSubscriptionPlan().getFeatures().contains(PlanFeatures.WEBHOOK)
                        && (!event.eventType().equals("METER_EPISODE_OPENED") || e.getCompany().getSubscription().getSubscriptionPlan().getFeatures().contains(PlanFeatures.METER)))
                .map(e->new WebhookDestination(e.getUrl(),e.getSecret(),e.isSerialize()));
    }
}
