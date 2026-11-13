package com.grash.integration;

import com.grash.event.*;
import com.grash.exception.CustomException;
import com.grash.factory.MailServiceFactory;
import com.grash.model.*;
import com.grash.model.enums.*;
import com.grash.security.FreshMaintenanceActor;
import com.grash.service.*;
import com.grash.sla.MaintenanceSlaService;
import com.grash.utils.Helper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DB_URL=127.0.0.1:25432/cmms_enhancement_test", "DB_USER=cmms_test", "DB_PWD=cmms_test_local",
        "JWT_SECRET_KEY=local-test-only-256-bit-jwt-secret-value-2026",
        "PUBLIC_FRONT_URL=http://127.0.0.1:28089", "PUBLIC_API_URL=http://127.0.0.1:28089",
        "MAIL_RECIPIENTS=test@example.invalid", "STORAGE_TYPE=MINIO", "LICENSE_KEY=", "license-key=", "LICENSE_FILE_PATH=",
        "LOGO_PATHS=", "ENABLE_EMAIL_NOTIFICATIONS=false", "SENTRY_DSN=", "INTERCOM_TOKEN=", "PADDLE_API_KEY=",
        "GCP_JSON=", "GCP_JSON_PATH=", "MINIO_ENDPOINT=", "MINIO_BUCKET=", "MINIO_ACCESS_KEY=", "MINIO_SECRET_KEY=", "PUBLIC_MINIO_ENDPOINT=",
        "cmms.sla.enabled=true", "cmms.events.enabled=true", "cmms.events.external-delivery-enabled=false",
        "cmms.events.workers-enabled=false", "cmms.events.listener-auto-startup=false",
        "cmms.events.publish-poll-ms=3600000", "cmms.events.delivery-poll-ms=3600000",
        "spring.quartz.auto-startup=false", "spring.jpa.hibernate.ddl-auto=validate", "spring.sql.init.mode=never",
        "spring.liquibase.enabled=true", "spring.liquibase.change-log=classpath:/db/master.xml"
})
@ActiveProfiles({"test", "maintenance-lab"})
@EnabledIfSystemProperty(named = "cmms.test.jdbc-url", matches = ".+")
class MaintenanceSlaRequestIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        IsolatedTestDatabase.configure(registry,"sla-request");
        registry.add("spring.rabbitmq.username",()->System.getProperty("cmms.test.rabbit-user","cmms_test"));
        registry.add("spring.rabbitmq.password",()->System.getProperty("cmms.test.rabbit-password","cmms_test_local"));
    }
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired RequestLinkCommandService links;
    @Autowired RequestService requests;
    @Autowired MaintenanceSlaService sla;
    @Autowired FreshMaintenanceActor actors;
    @MockitoBean MailServiceFactory mail;
    @MockitoBean WebhookDispatchService webhooks;
    @MockitoBean MinioService minio;
    @MockitoBean GCPService gcp;
    TransactionTemplate tx;long companyId,actorId,assetId,firstId,secondId,requestId,roleId;User actor;

    @BeforeEach void fixture() {
        when(mail.getMailService()).thenReturn(mock(MailService.class));
        tx=new TransactionTemplate(transactionManager);SecurityContextHolder.clearContext();
        tx.executeWithoutResult(status->{
            SubscriptionPlan plan=new SubscriptionPlan();plan.setName("Synthetic SLA request");plan.setCode("TEST");plan.setFeatures(new HashSet<>(List.of(PlanFeatures.ANALYTICS)));em.persist(plan);
            Subscription subscription=new Subscription();subscription.setUsersCount(5);subscription.setSubscriptionPlan(plan);em.persist(subscription);
            CompanySettings settings=new CompanySettings();em.persist(settings);
            Company company=new Company("SLA fixture "+UUID.randomUUID(),2,subscription);company.setCompanySettings(settings);em.persist(company);settings.setCompany(company);settings.getGeneralPreferences().setTimeZone("UTC");companyId=company.getId();
            Set<PermissionEntity> permissions=new HashSet<>(List.of(PermissionEntity.WORK_ORDERS,PermissionEntity.REQUESTS,PermissionEntity.ASSETS,PermissionEntity.SETTINGS));
            Role role=Role.builder().name("Synthetic manager").roleType(RoleType.ROLE_CLIENT).code(RoleCode.ADMIN).companySettings(settings)
                    .viewPermissions(new HashSet<>(permissions)).viewOtherPermissions(new HashSet<>(permissions)).editOtherPermissions(new HashSet<>(permissions)).createPermissions(new HashSet<>(permissions)).deleteOtherPermissions(new HashSet<>(permissions)).build();
            em.persist(role);roleId=role.getId();
            User user=new User();user.setFirstName("Synthetic");user.setLastName("Manager");user.setEmail("sla-"+UUID.randomUUID()+"@example.invalid");user.setUsername(user.getEmail());user.setPassword("not-login");user.setCompany(company);user.setRole(role);user.setEnabled(true);user.setEnabledInSubscription(true);user.setLanguage(Language.EN);em.persist(user);actorId=user.getId();Helper.setCurrentUser(user);
            Asset asset=new Asset();asset.setName("Fixture pump");asset.setCompany(company);asset.setStatus(AssetStatus.OPERATIONAL);em.persist(asset);assetId=asset.getId();
            WorkOrder first=order(company,asset,"Pump maintenance");em.persist(first);firstId=first.getId();
            WorkOrder second=order(company,asset,"Pump inspection");em.persist(second);secondId=second.getId();
            Request request=new Request();request.setCompany(company);request.setAsset(asset);request.setTitle("Pump noise");request.setCustomId("R-FIXTURE");em.persist(request);requestId=request.getId();
            em.persist(new CustomSequence(company));em.flush();em.clear();
        });
        SecurityContextHolder.clearContext();User identity=new User();identity.setId(actorId);Company c=new Company();c.setId(companyId);identity.setCompany(c);actor=actors.load(identity);Helper.setCurrentUser(actor);
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext(); if (jdbc != null && companyId > 0) jdbc.update("UPDATE work_order SET archived=true WHERE company_id=?",companyId);}
    private WorkOrder order(Company company,Asset asset,String title){WorkOrder w=new WorkOrder();w.setCompany(company);w.setAsset(asset);w.setTitle(title);w.setPriority(Priority.HIGH);w.setStatus(Status.OPEN);w.setCustomId("W-"+UUID.randomUUID().toString().substring(0,8));return w;}
    private int count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE company_id=?",Integer.class,companyId);}
    private long anotherRequest(){return tx.execute(s->{Request r=new Request();r.setCompany(em.find(Company.class,companyId));r.setAsset(em.find(Asset.class,assetId));r.setTitle("Pump repeated noise");em.persist(r);em.flush();return r.getId();});}
    private void enablePolicy(){sla.savePolicy(Priority.HIGH,1,2,true,actor);}
    private void makeDue(){jdbc.update("UPDATE maintenance_sla_case SET started_at=CURRENT_TIMESTAMP-INTERVAL '5 minutes',response_due_at=CURRENT_TIMESTAMP-INTERVAL '4 minutes',repair_due_at=CURRENT_TIMESTAMP-INTERVAL '3 minutes' WHERE company_id=?",companyId);}

    @Test void manyRequestsShareOneOrderAndDuplicateLinkHasOneAuditAndEvent(){
        long extra=anotherRequest();
        var first=links.link(requestId,firstId,"同一设备重复报修",actor);
        var replay=links.link(requestId,firstId,"同一设备重复报修",actor);
        links.link(extra,firstId,"相同故障一起维修",actor);
        assertFalse(first.replayed());assertTrue(replay.replayed());assertEquals(first.linkedAt(),replay.linkedAt());
        assertEquals(2,count("request_link_audit"));assertEquals(2,count("domain_event_outbox"));assertEquals(2,count("work_order"));
        tx.executeWithoutResult(s->{WorkOrder order=em.find(WorkOrder.class,firstId);assertEquals(2,order.getLinkedRequests().size());assertNull(order.getParentRequest());});
    }
    @Test void deletingLinkedOrderRetainsAuditAndAllowsExplicitRelinking() {
        links.link(requestId,firstId,"首次关联同设备维修工单",actor);
        tx.executeWithoutResult(status->{em.remove(em.find(WorkOrder.class,firstId));em.flush();em.clear();});
        assertNull(jdbc.queryForObject("SELECT work_order_id FROM request WHERE id=?",Long.class,requestId));
        links.link(requestId,secondId,"原工单删除后重新关联",actor);
        assertEquals(2,count("request_link_audit"));assertEquals(2,count("domain_event_outbox"));
        assertEquals(secondId,jdbc.queryForObject("SELECT work_order_id FROM request WHERE id=?",Long.class,requestId));
    }
    @Test void deletingDuplicateRequestDoesNotClearOriginalParentRequest() {
        long duplicate=anotherRequest();
        tx.executeWithoutResult(status->{WorkOrder order=em.find(WorkOrder.class,firstId);Request original=em.find(Request.class,requestId);original.setWorkOrder(order);order.setParentRequest(original);em.flush();});
        links.link(duplicate,firstId,"关联已有原始报修的工单",actor);
        tx.executeWithoutResult(status->{em.remove(em.find(Request.class,duplicate));em.flush();em.clear();});
        assertEquals(requestId,jdbc.queryForObject("SELECT parent_request_id FROM work_order WHERE id=?",Long.class,firstId));
    }
    @Test void concurrentDifferentTargetsChooseOnlyOne() throws Exception {
        ExecutorService workers=Executors.newFixedThreadPool(2);CyclicBarrier gate=new CyclicBarrier(2);
        try {
            Callable<Boolean> a=()->attemptLink(firstId,gate),b=()->attemptLink(secondId,gate);
            Future<Boolean> left=workers.submit(a),right=workers.submit(b);
            assertNotEquals(left.get(25,TimeUnit.SECONDS),right.get(25,TimeUnit.SECONDS));assertEquals(1,count("request_link_audit"));assertEquals(1,count("domain_event_outbox"));
        } finally {workers.shutdownNow();}
    }
    private boolean attemptLink(long target,CyclicBarrier gate) throws Exception {Helper.setCurrentUser(actor);try{gate.await(5,TimeUnit.SECONDS);links.link(requestId,target,"并发关联同一个报修",actor);return true;}catch(CustomException e){return false;}finally{SecurityContextHolder.clearContext();}}
    @Test void linkAndCancellationCannotBothWin() throws Exception {
        ExecutorService workers=Executors.newFixedThreadPool(2);CyclicBarrier gate=new CyclicBarrier(2);
        try {
            Future<Boolean> linked=workers.submit(()->attemptLink(firstId,gate));
            Future<Boolean> cancelled=workers.submit(()->{Helper.setCurrentUser(actor);try{gate.await(5,TimeUnit.SECONDS);requests.cancel(requestId,"重复报修取消",actor);return true;}catch(CustomException e){return false;}finally{SecurityContextHolder.clearContext();}});
            assertNotEquals(linked.get(25,TimeUnit.SECONDS),cancelled.get(25,TimeUnit.SECONDS));
            Map<String,Object> row=jdbc.queryForMap("SELECT cancelled,work_order_id FROM request WHERE id=?",requestId);
            assertFalse(Boolean.TRUE.equals(row.get("cancelled")) && row.get("work_order_id")!=null);
        } finally {workers.shutdownNow();}
    }
    @Test void cancellationCompletedOrderAndWrongEquipmentAreRejected(){
        jdbc.update("UPDATE request SET cancelled=true WHERE id=?",requestId);assertThrows(CustomException.class,()->links.link(requestId,firstId,"已取消不能关联工单",actor));
        jdbc.update("UPDATE request SET cancelled=false WHERE id=?",requestId);jdbc.update("UPDATE work_order SET status=? WHERE id=?",Status.COMPLETE.ordinal(),firstId);
        assertThrows(CustomException.class,()->links.link(requestId,firstId,"已完成不能关联工单",actor));
        jdbc.update("UPDATE work_order SET status=?,asset_id=NULL WHERE id=?",Status.OPEN.ordinal(),firstId);
        assertThrows(CustomException.class,()->links.link(requestId,firstId,"不同设备不能关联工单",actor));assertEquals(0,count("request_link_audit"));
    }
    @Test void foreignCompanyCannotBeLinkedOrEnrolled() {
        SecurityContextHolder.clearContext();
        long foreign=tx.execute(status->{
            SubscriptionPlan plan=new SubscriptionPlan();plan.setName("Other fixture");plan.setCode("TEST");em.persist(plan);
            Subscription sub=new Subscription();sub.setSubscriptionPlan(plan);em.persist(sub);
            CompanySettings settings=new CompanySettings();em.persist(settings);
            Company company=new Company("Other tenant "+UUID.randomUUID(),2,sub);company.setCompanySettings(settings);em.persist(company);settings.setCompany(company);
            WorkOrder w=order(company,null,"Foreign company repair");em.persist(w);em.flush();return w.getId();
        });
        Helper.setCurrentUser(actor);
        assertThrows(CustomException.class,()->links.link(requestId,foreign,"禁止跨企业关联维修工单",actor));
        assertThrows(CustomException.class,()->sla.enroll(foreign,"禁止跨企业纳管维修工单",actor));
        assertEquals(0,count("request_link_audit"));assertEquals(0,count("maintenance_sla_case"));
    }
    @Test void linkAndOutboxRollbackWithOuterTransaction(){
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->{links.link(requestId,firstId,"注入故障验证全部回滚",actor);throw new IllegalStateException("injected");}));
        assertNull(jdbc.queryForObject("SELECT work_order_id FROM request WHERE id=?",Long.class,requestId));assertEquals(0,count("request_link_audit"));assertEquals(0,count("domain_event_outbox"));
    }
    @Test void policyDoesNotSilentlyEnrollHistoryAndSnapshotsNewOrder(){
        enablePolicy();assertEquals(0,count("maintenance_sla_case"));
        long added=tx.execute(s->{WorkOrder w=order(em.find(Company.class,companyId),em.find(Asset.class,assetId),"New policy order");em.persist(w);em.flush();return w.getId();});
        assertEquals(1,count("maintenance_sla_case"));Timestamp due=jdbc.queryForObject("SELECT repair_due_at FROM maintenance_sla_case WHERE work_order_id=?",Timestamp.class,added);
        sla.savePolicy(Priority.HIGH,3,6,true,actor);jdbc.update("UPDATE work_order SET priority=? WHERE id=?",Priority.LOW.ordinal(),added);
        assertEquals(due,jdbc.queryForObject("SELECT repair_due_at FROM maintenance_sla_case WHERE work_order_id=?",Timestamp.class,added));
    }
    @Test void captureFirstResponseAndCompletionEvenIfOrderReopens(){
        enablePolicy();sla.enroll(firstId,"显式纳管旧设备维修工单",actor);
        Instant reaction=Instant.now();jdbc.update("UPDATE work_order SET first_time_to_react=?,completed_on=?,status=? WHERE id=?",Timestamp.from(reaction),Timestamp.from(reaction),Status.COMPLETE.ordinal(),firstId);
        Timestamp completed=jdbc.queryForObject("SELECT completed_at FROM maintenance_sla_case WHERE work_order_id=?",Timestamp.class,firstId);
        jdbc.update("UPDATE work_order SET completed_on=NULL,status=? WHERE id=?",Status.OPEN.ordinal(),firstId);
        assertEquals(completed,jdbc.queryForObject("SELECT completed_at FROM maintenance_sla_case WHERE work_order_id=?",Timestamp.class,firstId));assertNotNull(completed);
    }
    @Test void concurrentSlaWorkersCreateOneEscalationPerKind() throws Exception {
        enablePolicy();sla.enroll(firstId,"并发扫描验证超时幂等",actor);makeDue();
        ExecutorService workers=Executors.newFixedThreadPool(2);CyclicBarrier gate=new CyclicBarrier(2);
        try {Callable<Integer> job=()->{SecurityContextHolder.clearContext();gate.await(5,TimeUnit.SECONDS);return sla.processDue();};Future<Integer>a=workers.submit(job),b=workers.submit(job);assertEquals(2,a.get(25,TimeUnit.SECONDS)+b.get(25,TimeUnit.SECONDS));}
        finally {workers.shutdownNow();}
        SecurityContextHolder.clearContext();assertEquals(0,sla.processDue());assertEquals(2,count("maintenance_sla_escalation"));assertEquals(2,count("domain_event_outbox"));
    }
    @Test void slaEscalationAndOutboxRollbackTogether(){
        enablePolicy();sla.enroll(firstId,"故障注入验证超时回滚",actor);makeDue();
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->{sla.processDue();throw new IllegalStateException("after outbox");}));
        assertEquals(0,count("maintenance_sla_escalation"));assertEquals(0,count("domain_event_outbox"));assertEquals(2,sla.processDue());
    }
    @Test void timelyCompletionDoesNotEscalateAndSummaryIsTenantScoped(){
        enablePolicy();sla.enroll(firstId,"验证按时维修没有升级",actor);
        jdbc.update("UPDATE work_order SET first_time_to_react=created_at,completed_on=created_at,status=? WHERE id=?",Status.COMPLETE.ordinal(),firstId);
        assertEquals(0,sla.processDue());var board=sla.board(actor,0,10);assertEquals(1,board.totalElements());assertEquals(1,board.completed());assertEquals(0,board.responseBreached());assertEquals(0,board.repairBreached());
    }
    @Test void freshPermissionsAreCheckedOnLinkReplayAndSlaReads(){
        links.link(requestId,firstId,"验证撤权以后禁止重放",actor);
        tx.executeWithoutResult(s->{Role r=em.find(Role.class,roleId);r.getViewOtherPermissions().clear();em.flush();});
        assertThrows(CustomException.class,()->links.link(requestId,firstId,"验证撤权以后禁止重放",actor));assertThrows(CustomException.class,()->sla.board(actor,0,10));assertEquals(1,count("request_link_audit"));
    }
}
