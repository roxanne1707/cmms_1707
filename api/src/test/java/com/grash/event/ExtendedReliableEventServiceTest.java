package com.grash.event;
import com.grash.dto.license.LicenseEntitlement;
import com.grash.mapper.WorkOrderMapper;
import com.grash.model.*;
import com.grash.model.enums.*;
import com.grash.model.enums.webhook.WebhookEvent;
import com.grash.repository.*;
import com.grash.service.LicenseService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ExtendedReliableEventServiceTest {
    private final ReliableEventProperties properties=new ReliableEventProperties();
    private final EventStore store=mock(EventStore.class);
    private final UserRepository users=mock(UserRepository.class);
    private final WebhookEndpointRepository endpoints=mock(WebhookEndpointRepository.class);
    private final LicenseService license=mock(LicenseService.class);
    private final ReportAttachmentStore attachments=mock(ReportAttachmentStore.class);
    private final RequestRepository requests=mock(RequestRepository.class);
    private final WorkOrderRepository orders=mock(WorkOrderRepository.class);
    private final MessageSource messages=mock(MessageSource.class);
    private final ReliableEventService service=new ReliableEventService(properties,store,mock(WorkOrderMapper.class),messages,users,endpoints,license,attachments,requests,orders);
    private Company company;
    private User admin;
    private WorkOrder order;
    @BeforeEach void fixture() {
        properties.setEnabled(true);ReflectionTestUtils.setField(service,"frontendUrl","http://127.0.0.1:3289");
        company=new Company();company.setId(1L);CompanySettings settings=new CompanySettings();settings.setGeneralPreferences(new GeneralPreferences(settings));company.setCompanySettings(settings);
        Subscription subscription=new Subscription();SubscriptionPlan plan=mock(SubscriptionPlan.class);when(plan.getFeatures()).thenReturn(Set.of(PlanFeatures.WEBHOOK));subscription.setSubscriptionPlan(plan);company.setSubscription(subscription);
        admin=new User();admin.setId(11L);admin.setCompany(company);admin.setEnabled(true);admin.setEnabledInSubscription(true);admin.setEmail("admin@example.invalid");admin.setFirstName("Admin");admin.setLastName("Test");admin.setUserSettings(new UserSettings());
        Role role=new Role();role.setViewPermissions(Set.of(PermissionEntity.SETTINGS,PermissionEntity.WORK_ORDERS));role.setViewOtherPermissions(Set.of(PermissionEntity.WORK_ORDERS));admin.setRole(role);
        order=new WorkOrder();order.setId(7L);order.setTitle("Pump repair");order.setStatus(Status.OPEN);order.setCompany(company);
        when(users.findByCompany_Id(1L)).thenReturn(List.of(admin));
        when(users.findByIdAndCompany_Id(11L,1L)).thenReturn(Optional.of(admin));
        when(messages.getMessage(anyString(),any(),anyString(),any())).thenAnswer(invocation->invocation.getArgument(0));
    }
    private EventEnvelope captured() {var captor=ArgumentCaptor.forClass(EventEnvelope.class);verify(store).append(captor.capture());return captor.getValue();}
    @Test void deletionSnapshotsAdminEmailAndWebhookBeforeResourceDisappears() {
        when(license.hasEntitlement(LicenseEntitlement.WEBHOOK)).thenReturn(true);
        WebhookEndpoint endpoint=new WebhookEndpoint();endpoint.setId(21L);endpoint.setEvent(WebhookEvent.WORK_ORDER_DELETE);
        when(endpoints.findByCompanyIdAndEnabled(1L,true)).thenReturn(List.of(endpoint));
        service.workOrderDeleted(order,11L);EventEnvelope event=captured();
        assertEquals("WORK_ORDER_DELETED",event.eventType());assertEquals("WORK_ORDER_DELETED",event.targets().get(0).purpose());
        assertEquals("deleted-work-order.html",event.targets().get(0).template());assertEquals("deleteWorkOrder",event.webhooks().get(0).serializedField());
    }
    @Test void lowStockRecipientsAreDeduplicatedWhenAdminIsAlsoAssigned() {
        when(license.hasEntitlement(LicenseEntitlement.LOW_STOCK_ALERTS)).thenReturn(true);
        Part part=new Part();part.setId(3L);part.setName("Bearing");part.setCompany(company);part.setQuantity(2);part.setMinQuantity(5);part.setAssignedTo(List.of(admin));
        service.partQuantityChanged(part,6,2,order,Map.of("id",3));EventEnvelope event=captured();
        assertEquals(1,event.targets().size());assertEquals("LOW_STOCK",event.targets().get(0).purpose());assertEquals(2d,event.facts().get("newQuantity"));
    }
    @Test void reportPersistsAttachmentBeforeEventAndDoesNotEmbedPdf() {
        when(orders.findByIdAndCompany_Id(7L,1L)).thenReturn(Optional.of(order));
        Customer customer=new Customer();customer.setId(5L);customer.setCompany(company);customer.setEmail("customer@example.invalid");
        byte[] pdf="%PDF-test".getBytes();service.workOrderReport(order,admin,List.of(customer),"Report","Body","report.pdf",pdf);
        EventEnvelope event=captured();assertEquals(2,event.targets().size());assertEquals("WORK_ORDER_REPORT",event.eventType());
        var inOrder=inOrder(attachments,store);inOrder.verify(attachments).save(eq(1L),eq(event.eventId()),eq("report.pdf"),same(pdf));inOrder.verify(store).append(event);
        assertFalse(event.facts().containsKey("pdf"));assertEquals(5L,event.targets().get(0).variables().get("customerId"));
    }
    @Test void statusChangeNotifiesAllAssociatedRequestsWithTheirOwnIdentity() {
        Request first=new Request();first.setId(31L);first.setContact("first@example.invalid");
        Request second=new Request();second.setId(32L);second.setContact("second@example.invalid");
        when(requests.findByCompany_IdAndWorkOrder_Id(1L,7L)).thenReturn(List.of(first,second));order.setParentRequest(first);order.setStatus(Status.IN_PROGRESS);
        service.workOrderStatusChanged(order,Status.OPEN,11L);EventEnvelope event=captured();
        assertEquals(2,event.targets().size());assertEquals(Set.of(31L,32L),event.targets().stream().map(t->t.variables().get("requestId")).collect(java.util.stream.Collectors.toSet()));
    }
}
