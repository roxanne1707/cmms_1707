package com.grash.event;

import com.grash.dto.license.LicenseEntitlement;
import com.grash.model.*;
import com.grash.model.enums.PlanFeatures;
import com.grash.model.enums.webhook.WebhookEvent;
import com.grash.repository.*;
import com.grash.service.LicenseService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventAccessPolicyTest {
    private final UserRepository users=mock(UserRepository.class);
    private final WorkOrderRepository orders=mock(WorkOrderRepository.class);
    private final MeterRepository meters=mock(MeterRepository.class);
    private final WebhookEndpointRepository endpoints=mock(WebhookEndpointRepository.class);
    private final LicenseService license=mock(LicenseService.class);
    private final EventAccessPolicy policy=new EventAccessPolicy(users,orders,meters,endpoints,mock(PushNotificationTokenRepository.class),license,mock(JdbcTemplate.class),mock(PartRepository.class),mock(PreventiveMaintenanceRepository.class));
    @Test void disabledSubscriptionSeatIsRejectedBeforeResourceRead() {
        User user=new User();user.setEnabled(true);user.setEnabledInSubscription(false);
        when(users.findByIdAndCompany_Id(11L,1L)).thenReturn(Optional.of(user));
        assertFalse(policy.canReceive(event("WORK_ORDER_CREATED"),target("WORK_ORDER")));
        verifyNoInteractions(orders,meters);
    }
    @Test void revokedMeterSubscriptionIsRejectedEvenIfRecipientPreviouslyHadAccess() {
        User user=new User();user.setEnabled(true);user.setCompany(company(List.of()));
        when(users.findByIdAndCompany_Id(11L,1L)).thenReturn(Optional.of(user));
        assertFalse(policy.canReceive(event("METER_EPISODE_OPENED"),target("METER")));
        verifyNoInteractions(meters);
    }
    @Test void webhookRequiresBothCurrentLicenseAndSubscriptionAndMeterFeature() {
        WebhookEndpoint endpoint=new WebhookEndpoint();endpoint.setEnabled(true);endpoint.setCompany(company(List.of(PlanFeatures.WEBHOOK)));
        endpoint.setEvent(WebhookEvent.METER_TRIGGER_STATUS_CHANGE);endpoint.setUrl("https://example.invalid/hook");endpoint.setSecret("test-secret");
        when(endpoints.findByIdAndCompanyId(9L,1L)).thenReturn(Optional.of(endpoint));
        var target=new EventEnvelope.WebhookTarget(9,"METER_TRIGGER_STATUS_CHANGE",false,"triggeredWorkOrder");
        when(license.hasEntitlement(LicenseEntitlement.WEBHOOK)).thenReturn(true);
        assertTrue(policy.webhook(event("METER_EPISODE_OPENED"),target).isEmpty());
        endpoint.setCompany(company(List.of(PlanFeatures.WEBHOOK,PlanFeatures.METER)));
        assertTrue(policy.webhook(event("METER_EPISODE_OPENED"),target).isPresent());
        when(license.hasEntitlement(LicenseEntitlement.WEBHOOK)).thenReturn(false);
        assertTrue(policy.webhook(event("METER_EPISODE_OPENED"),target).isEmpty());
    }
    private Company company(List<PlanFeatures> features) {
        Company company=new Company();company.setId(1L);Subscription subscription=new Subscription();
        SubscriptionPlan plan=mock(SubscriptionPlan.class);when(plan.getFeatures()).thenReturn(new HashSet<>(features));
        subscription.setSubscriptionPlan(plan);company.setSubscription(subscription);return company;
    }
    private EventEnvelope event(String type) {return new EventEnvelope(1,UUID.randomUUID(),1,type,1,null,Instant.now(),List.of(),List.of(),Map.of());}
    private EventEnvelope.Target target(String type) {return new EventEnvelope.Target(11L,"test@example.invalid","ASSIGNMENT","message","title",type,1,null,"en",Map.of(),false,false);}
}
