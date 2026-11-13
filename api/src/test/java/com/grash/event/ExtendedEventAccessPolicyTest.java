package com.grash.event;
import com.grash.dto.license.LicenseEntitlement;
import com.grash.model.*;
import com.grash.repository.*;
import com.grash.service.LicenseService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ExtendedEventAccessPolicyTest {
    private final UserRepository users=mock(UserRepository.class);
    private final WorkOrderRepository orders=mock(WorkOrderRepository.class);
    private final PartRepository parts=mock(PartRepository.class);
    private final LicenseService license=mock(LicenseService.class);
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final EventAccessPolicy policy=new EventAccessPolicy(users,orders,mock(MeterRepository.class),mock(WebhookEndpointRepository.class),mock(PushNotificationTokenRepository.class),license,jdbc,parts,mock(PreventiveMaintenanceRepository.class));
    private EventEnvelope event() {return new EventEnvelope(1,UUID.randomUUID(),1,"WORK_ORDER_REPORT",7,11L,Instant.now(),List.of(),List.of(),Map.of("requestId",999));}
    private EventEnvelope.Target target(String purpose,Long userId,Map<String,Object> variables) {return new EventEnvelope.Target(userId,"customer@example.invalid",purpose,"body","subject",purpose.equals("LOW_STOCK")?"PART":"WORK_ORDER",7,"template","en",variables,true,false);}
    private User actor() {
        User value=new User();value.setId(11L);value.setEnabled(true);value.setEnabledInSubscription(true);value.setEmail("actor@example.invalid");
        when(users.findByIdAndCompany_Id(11L,1L)).thenReturn(Optional.of(value));
        WorkOrder order=mock(WorkOrder.class);when(order.canBeViewedBy(value)).thenReturn(true);when(orders.findByIdAndCompany_Id(7L,1L)).thenReturn(Optional.of(order));return value;
    }
    @Test void reportCustomerMustStillBelongToCompanyAndKeepSameEmail() {
        actor();var target=target("WORK_ORDER_REPORT",null,Map.of("customerId",5));
        when(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM customer WHERE id=? AND company_id=? AND email=?)",Boolean.class,5L,1L,"customer@example.invalid")).thenReturn(true,false);
        assertTrue(policy.canEmail(event(),target));assertFalse(policy.canEmail(event(),target));
    }
    @Test void revokedReportActorCannotSendPreviouslyQueuedSnapshot() {
        User user=actor();user.setEnabled(false);
        assertFalse(policy.canReceive(event(),target("WORK_ORDER_REPORT",null,Map.of("customerId",5))));verifyNoInteractions(jdbc);
    }
    @Test void eachAnonymousLinkedRequestUsesItsOwnRequestIdentity() {
        var target=target("REQUEST_STATUS",null,Map.of("requestId",123));
        when(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM request WHERE id=? AND company_id=? AND contact=? AND work_order_id=?)",Boolean.class,123L,1L,"customer@example.invalid",7L)).thenReturn(true);
        assertTrue(policy.canReceive(event(),target));
    }
    @Test void lowStockRequiresCurrentLicenseAndCurrentPartVisibility() {
        User user=actor();Part part=mock(Part.class);when(parts.findByIdAndCompany_Id(7L,1L)).thenReturn(Optional.of(part));when(part.canBeViewedBy(user)).thenReturn(true);
        var target=target("LOW_STOCK",11L,Map.of());
        assertFalse(policy.canReceive(event(),target));when(license.hasEntitlement(LicenseEntitlement.LOW_STOCK_ALERTS)).thenReturn(true);assertTrue(policy.canReceive(event(),target));
        when(part.canBeViewedBy(user)).thenReturn(false);assertFalse(policy.canReceive(event(),target));
    }
}
