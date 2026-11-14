package com.grash.integration;

import com.grash.dto.PartQuantityShowDTO;
import com.grash.dto.query.AssetWorkOrderOverview;
import com.grash.dto.query.OverviewQuery;
import com.grash.event.*;
import com.grash.exception.CustomException;
import com.grash.factory.MailServiceFactory;
import com.grash.model.*;
import com.grash.model.enums.*;
import com.grash.security.FreshMaintenanceActor;
import com.grash.service.*;
import com.grash.service.query.AssetWorkOrderOverviewService;
import com.grash.utils.Helper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Full application + real Hibernate repositories + full Liquibase master on the dedicated disposable DB.
 * Only outbound adapters are replaced. No test-wide transaction hides real commits or concurrent sessions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DB_URL=127.0.0.1:25432/cmms_enhancement_test", "DB_USER=cmms_test", "DB_PWD=cmms_test_local",
        "JWT_SECRET_KEY=local-test-only-256-bit-jwt-secret-value-2026",
        "PUBLIC_FRONT_URL=http://127.0.0.1:28089", "PUBLIC_API_URL=http://127.0.0.1:28089",
        "MAIL_RECIPIENTS=test@example.invalid", "STORAGE_TYPE=MINIO", "LICENSE_KEY=", "license-key=", "LICENSE_FILE_PATH=",
        "LOGO_PATHS=", "ENABLE_EMAIL_NOTIFICATIONS=false", "SENTRY_DSN=", "INTERCOM_TOKEN=", "PADDLE_API_KEY=",
        "GCP_JSON=", "GCP_JSON_PATH=", "MINIO_ENDPOINT=", "MINIO_BUCKET=", "MINIO_ACCESS_KEY=", "MINIO_SECRET_KEY=", "PUBLIC_MINIO_ENDPOINT=",
        "cmms.events.enabled=true", "cmms.events.external-delivery-enabled=false",
        "cmms.events.workers-enabled=false", "cmms.events.listener-auto-startup=false",
        "cmms.events.publish-poll-ms=3600000", "cmms.events.delivery-poll-ms=3600000",
        "security.rate-limit.enabled=true", "security.rate-limit.unauthenticated.short-term-requests=2",
        "security.rate-limit.unauthenticated.long-term-requests=2", "cmms.lab.metrics-password=local-metrics-isolation-test",
        "spring.quartz.auto-startup=false", "spring.jpa.hibernate.ddl-auto=validate", "spring.sql.init.mode=never",
        "spring.liquibase.enabled=true", "spring.liquibase.change-log=classpath:/db/master.xml"
})
@ActiveProfiles({"test", "maintenance-lab"})
@EnabledIfSystemProperty(named = "cmms.test.jdbc-url", matches = ".+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MaintenanceJpaIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        IsolatedTestDatabase.configure(registry, "maintenance-jpa");
        registry.add("spring.rabbitmq.username", () -> System.getProperty("cmms.test.rabbit-user", "guest"));
        registry.add("spring.rabbitmq.password", () -> System.getProperty("cmms.test.rabbit-password", "guest"));
    }

    @Autowired org.springframework.boot.test.web.client.TestRestTemplate http;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.grash.database.DatabasePlatform database;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired InventoryCommandService inventory;
    @Autowired ReadingService readings;
    @Autowired WorkOrderMeterTriggerService triggerService;
    @Autowired com.grash.mapper.WorkOrderMeterTriggerMapper triggerMapper;
    @Autowired FreshMaintenanceActor actors;
    @Autowired AssetWorkOrderOverviewService overview;
    @Autowired ReliableEventService reliableEvents;
    @Autowired EventStore eventStore;
    @Autowired EventJson eventJson;
    @Autowired NotificationCommandService notifications;
    @Autowired com.grash.service.query.MaintenanceLedgerJpaRepository ledgerBaseline;
    @Autowired com.grash.readmodel.mapper.MaintenanceLedgerMapper ledgerMapper;
    @Autowired EventReplayService replay;
    @Autowired com.grash.writemodel.mapper.NotificationReplayAuditMapper replayAudits;
    @Autowired com.grash.security.SessionRegistry sessions;
    @Autowired com.grash.repository.WorkOrderRepository orderRepository;
    @Autowired com.grash.repository.AssetDowntimeRepository downtimeRepository;

    @MockitoBean MailServiceFactory mailServiceFactory;
    @MockitoBean WebhookDispatchService webhooks;
    @MockitoBean MinioService minio;
    @MockitoBean GCPService gcp;

    private TransactionTemplate tx;
    private Long companyId, actorId, partId, firstWorkOrderId, secondWorkOrderId, assetId, meterId, triggerId;
    private User actor;

    @BeforeEach
    void fixture(TestInfo test) {
        when(mailServiceFactory.getMailService()).thenReturn(mock(MailService.class));
        tx = new TransactionTemplate(transactionManager);
        if (test.getTestMethod().orElseThrow().getName().equals("fullContextAndMasterMigrationAreUsable")) return;
        SecurityContextHolder.clearContext();
        tx.executeWithoutResult(status -> {
            SubscriptionPlan plan = new SubscriptionPlan();
            plan.setName("Synthetic maintenance integration"); plan.setCode("TEST");
            plan.setFeatures(new HashSet<>(List.of(PlanFeatures.METER, PlanFeatures.ANALYTICS)));
            em.persist(plan);
            Subscription subscription = new Subscription(); subscription.setUsersCount(5); subscription.setSubscriptionPlan(plan);
            em.persist(subscription);
            CompanySettings settings = new CompanySettings(); em.persist(settings);
            Company company = new Company("Maintenance JPA " + UUID.randomUUID(), 2, subscription);
            company.setCompanySettings(settings); em.persist(company); settings.setCompany(company);
            settings.getGeneralPreferences().setTimeZone("UTC");
            companyId = company.getId();
            Set<PermissionEntity> permissions = new HashSet<>(List.of(PermissionEntity.WORK_ORDERS,
                    PermissionEntity.PARTS_AND_MULTIPARTS, PermissionEntity.METERS, PermissionEntity.ASSETS,
                    PermissionEntity.PURCHASE_ORDERS, PermissionEntity.ANALYTICS));
            Role role = Role.builder().name("Synthetic maintainer").roleType(RoleType.ROLE_CLIENT).code(RoleCode.ADMIN)
                    .companySettings(settings).createPermissions(new HashSet<>(permissions))
                    .viewPermissions(new HashSet<>(permissions)).viewOtherPermissions(new HashSet<>(permissions))
                    .editOtherPermissions(new HashSet<>(permissions)).deleteOtherPermissions(new HashSet<>(permissions)).build();
            em.persist(role);
            User user = new User(); user.setFirstName("Synthetic"); user.setLastName("Maintainer");
            user.setEmail("maintenance-" + UUID.randomUUID() + "@example.invalid"); user.setUsername(user.getEmail());
            user.setPassword("not-a-real-login"); user.setCompany(company); user.setRole(role); user.setEnabled(true);
            user.setEnabledInSubscription(true); user.setLanguage(Language.EN); em.persist(user); actorId = user.getId();
            Helper.setCurrentUser(user);
            Asset asset = new Asset(); asset.setName("Synthetic pump"); asset.setCompany(company); asset.setStatus(AssetStatus.OPERATIONAL);
            em.persist(asset); assetId = asset.getId();
            Part part = new Part(); part.setName("Synthetic bearing"); part.setQuantity(10); part.setCompany(company);
            em.persist(part); partId = part.getId();
            WorkOrder first = new WorkOrder(); first.setCompany(company); first.setTitle("Pump repair"); first.setAsset(asset); first.setStatus(Status.OPEN);
            em.persist(first); firstWorkOrderId = first.getId();
            WorkOrder second = new WorkOrder(); second.setCompany(company); second.setTitle("Fan repair"); second.setAsset(asset); second.setStatus(Status.OPEN);
            em.persist(second); secondWorkOrderId = second.getId();
            Meter meter = new Meter(); meter.setCompany(company); meter.setName("Pressure"); meter.setUnit("bar");
            meter.setAsset(asset); meter.setUpdateFrequency(1); em.persist(meter); meterId = meter.getId();
            // Fixture inserts represent an existing configured rule; production license gates are never mocked.
            WorkOrderMeterTrigger rule = new WorkOrderMeterTrigger(); rule.setCompany(company); rule.setMeter(meter); rule.setAsset(asset);
            rule.setName("High pressure"); rule.setTitle("Inspect pressure"); rule.setTriggerCondition(WorkOrderMeterTriggerCondition.MORE_THAN);
            rule.setValue(10); rule.setEpisodePolicy(MeterEpisodePolicy.PER_EPISODE); em.persist(rule); triggerId = rule.getId();
            em.persist(new CustomSequence(company));
            em.flush(); em.clear();
        });
        SecurityContextHolder.clearContext();
        User identity = new User(); identity.setId(actorId);
        Company identityCompany = new Company(); identityCompany.setId(companyId); identity.setCompany(identityCompany);
        actor = actors.load(identity);
        Helper.setCurrentUser(actor);
    }

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
        // Synthetic fixtures are intentionally retained under unique company IDs for failure inspection.
        // This class never truncates a shared table or touches the original atlas database.
    }

    @Test @Order(1)
    void fullContextAndMasterMigrationAreUsable() {
        assertTrue(reliableEvents.isEnabled());
        assertTrue(jdbc.queryForObject(database.sql("SELECT count(*) FROM databasechangelog WHERE id='2026-11-14-domain-reliability'","SELECT count(*) FROM DATABASECHANGELOG WHERE id='mysql-fresh-baseline-20261114'"), Long.class) > 0);
        assertNotNull(jdbc.queryForObject(database.sql("SELECT to_regclass('meter_trigger_state')::text","SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='meter_trigger_state'"), String.class));
        assertNotNull(jdbc.queryForObject(database.sql("SELECT to_regclass('asset_summary_epoch')::text","SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='asset_summary_epoch'"), String.class));
    }

    @Test @Order(2)
    void actualHibernateInventoryCommandPersistsAndReplaysOnce() {
        PartQuantityShowDTO first = inventory.create(quantityRequest(firstWorkOrderId, 3), actor, "jpa-create");
        PartQuantityShowDTO replay = inventory.create(quantityRequest(firstWorkOrderId, 3), actor, "jpa-create");
        assertEquals(first.getId(), replay.getId()); assertEquals(7, stock());
        assertEquals(1, companyCount("part_quantity")); assertEquals(1, companyCount("part_transaction"));
        inventory.setQuantity(first.getId(), 3, actor, "jpa-zero");
        assertEquals(1, companyCount("part_transaction"));
        inventory.delete(first.getId(), actor, "jpa-return");
        inventory.delete(first.getId(), actor, "jpa-return");
        assertEquals(10, stock()); assertEquals(2, companyCount("part_transaction"));
        assertEquals(0, companyCount("part_quantity"));
    }

    @Test @Order(3)
    void actualJpaTransactionRollsBackInventoryAuditAndCommand() {
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            inventory.create(quantityRequest(firstWorkOrderId, 7), actor, "jpa-fail");
            em.flush();
            throw new IllegalStateException("Injected failure after real SQL flush");
        }));
        assertEquals(10, stock()); assertEquals(0, companyCount("part_quantity"));
        assertEquals(0, companyCount("part_transaction")); assertEquals(0, companyCount("domain_command"));
    }

    @Test @Order(4)
    void actualConcurrentHibernateSessionsCannotOverspend() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2); CyclicBarrier gate = new CyclicBarrier(2);
        try {
            Future<Boolean> a = workers.submit(() -> consumeConcurrently(firstWorkOrderId, "jpa-a", gate));
            Future<Boolean> b = workers.submit(() -> consumeConcurrently(secondWorkOrderId, "jpa-b", gate));
            assertNotEquals(a.get(25, TimeUnit.SECONDS), b.get(25, TimeUnit.SECONDS));
            assertEquals(3, stock()); assertEquals(1, companyCount("part_quantity")); assertEquals(1, companyCount("part_transaction"));
        } finally { workers.shutdownNow(); }
    }

    @Test @Order(5)
    void realReadingEpisodeNetworkReplayAndOutboxAreAtomic() {
        Reading abnormal = submit(15, "jpa-A");
        long outboxAfterFirst = companyCount("domain_event_outbox");
        assertTrue(outboxAfterFirst >= 2, "Work-order and meter episode events are persisted in the same transaction");
        ageLatestReading(); submit(16, "jpa-A2");
        assertEquals(1, companyCount("meter_trigger_execution"));
        ageLatestReading(); submit(5, "jpa-B");
        assertEquals(abnormal.getId(), submit(15, "jpa-A").getId());
        assertEquals(1, companyCount("meter_trigger_execution"));
        ageLatestReading(); submit(15, "jpa-C");
        assertEquals(2, companyCount("meter_trigger_execution"));
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM reading WHERE meter_id=?", Integer.class, meterId));
        long beforeOutbox = companyCount("domain_event_outbox");
        ageLatestReading(); submit(5, "jpa-recover"); ageLatestReading();
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            submit(15, "jpa-rollback-event"); em.flush();
            throw new IllegalStateException("Injected post-event failure");
        }));
        assertEquals(2, companyCount("meter_trigger_execution"));
        assertEquals(beforeOutbox, companyCount("domain_event_outbox"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM domain_command WHERE company_id=? AND command_key='jpa-rollback-event'", Integer.class, companyId));
    }

    @Test @Order(6)
    void realJpaWriteAdvancesEpochAndNewQuerySeesCommittedCounts() {
        OverviewQuery query = new OverviewQuery(); query.setStart(Instant.now().minus(1, ChronoUnit.DAYS)); query.setEnd(Instant.now().plus(1, ChronoUnit.DAYS));
        AssetWorkOrderOverview before = overview.overview(SecurityContextHolder.getContext().getAuthentication(), companyId, assetId, query);
        tx.executeWithoutResult(status -> {
            WorkOrder work = em.find(WorkOrder.class, firstWorkOrderId);
            work.setStatus(Status.COMPLETE); work.setCompletedOn(new Date()); em.flush();
        });
        AssetWorkOrderOverview after = overview.overview(SecurityContextHolder.getContext().getAuthentication(), companyId, assetId, query);
        assertTrue(after.epoch() > before.epoch()); assertEquals(before.total(), after.total());
        assertEquals(before.complete() + 1, after.complete());
    }

    @Test @Order(7)
    void productionMeterTriggerLicenseGateRemainsActive() {
        WorkOrderMeterTrigger rule = new WorkOrderMeterTrigger();
        assertThrows(CustomException.class, () -> triggerService.create(rule, actor.getCompany()));
    }

    @Test @Order(8)
    void realMeterEventConsumerUsesFullNotificationSchemaAndCurrentPermissions() {
        Long receiverId = tx.execute(status -> {
            User source = em.find(User.class, actorId);
            User receiver = new User(); receiver.setFirstName("Synthetic"); receiver.setLastName("Receiver");
            receiver.setEmail("receiver-" + UUID.randomUUID() + "@example.invalid"); receiver.setUsername(receiver.getEmail());
            receiver.setPassword("not-a-real-login"); receiver.setCompany(source.getCompany()); receiver.setRole(source.getRole());
            receiver.setEnabled(true); receiver.setEnabledInSubscription(true); receiver.setLanguage(Language.EN);
            em.persist(receiver);
            Meter meter = em.find(Meter.class, meterId); meter.getUsers().add(receiver);
            em.flush(); em.clear();
            return receiver.getId();
        });
        submit(15, "jpa-consume-allowed");
        DispatchMessage first = latestMeterDispatch();
        SecurityContextHolder.clearContext(); // A background consumer has no caller's security principal.
        EventStore.Attempt claimed = eventStore.claimAttempt(first).orElseThrow();
        notifications.consume(claimed);
        assertEquals(1, notificationCount(first.eventId(), receiverId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM event_delivery WHERE event_id=? AND company_id=? AND channel='PUSH' AND destination_id=?",
                Integer.class, first.eventId(), companyId, "user:" + receiverId));
        assertEquals("DONE", jdbc.queryForObject("SELECT state FROM event_retry WHERE id=?", String.class, claimed.id()));
        assertTrue(eventStore.claimAttempt(first).isEmpty(), "Broker redelivery must not repeat a committed consumer");
        assertEquals(1, notificationCount(first.eventId(), receiverId));

        Helper.setCurrentUser(actor);
        ageLatestReading(); submit(5, "jpa-consume-recover"); ageLatestReading(); submit(15, "jpa-consume-revoked");
        DispatchMessage second = latestMeterDispatch();
        tx.executeWithoutResult(status -> {
            em.find(User.class, receiverId).setEnabledInSubscription(false);
            em.flush(); em.clear();
        });
        SecurityContextHolder.clearContext();
        notifications.consume(eventStore.claimAttempt(second).orElseThrow());
        assertEquals(0, notificationCount(second.eventId(), receiverId), "Permission is rechecked at consumption, after event creation");
        assertEquals(2, companyCount("consumer_receipt"));
    }

    @Test @Order(9)
    void differentMetersAllocateDistinctWorkOrderNumbersWithStaleJpaSequences() throws Exception {
        Long secondMeterId = addSecondMeter();
        raceMeterCommands(secondMeterId, true);
        assertDistinctGeneratedWorkOrderNumbers();
    }

    @Test @Order(10)
    void concurrentFirstCommandsInitializeExactlyOneCompanySequence() throws Exception {
        Long secondMeterId = addSecondMeter();
        jdbc.update("DELETE FROM custom_sequence WHERE company_id=?", companyId);
        raceMeterCommands(secondMeterId, false);
        assertEquals(1, companyCount("custom_sequence"));
        assertDistinctGeneratedWorkOrderNumbers();
    }

    @Test @Order(11)
    void bulkRemovalRefreshesDetailsCachedBeforeTheParentLock() throws Exception {
        PartQuantityShowDTO quantity = inventory.create(quantityRequest(firstWorkOrderId, 3), actor, "jpa-bulk-initial");
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch cached = new CountDownLatch(1); CountDownLatch updated = new CountDownLatch(1);
        try {
            Future<?> removal = worker.submit(() -> {
                User fresh = actors.load(actor); Helper.setCurrentUser(fresh);
                try {
                    tx.executeWithoutResult(status -> {
                        assertEquals(3, em.find(PartQuantity.class, quantity.getId()).getQuantity());
                        cached.countDown();
                        try { assertTrue(updated.await(10, TimeUnit.SECONDS)); }
                        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                        inventory.replaceWorkOrder(firstWorkOrderId, List.of(), fresh, "jpa-bulk-remove");
                    });
                } finally { SecurityContextHolder.clearContext(); }
            });
            assertTrue(cached.await(10, TimeUnit.SECONDS));
            inventory.setQuantity(quantity.getId(), 5, actor, "jpa-bulk-new-quantity");
            assertEquals(5, stock());
            updated.countDown();
            removal.get(25, TimeUnit.SECONDS);
            assertEquals(10, stock(), "Return the committed quantity of five, not the previously cached quantity of three");
            assertEquals(0, companyCount("part_quantity"));
            assertEquals(3, companyCount("part_transaction"));
        } finally { updated.countDown(); worker.shutdownNow(); }
    }

    private Long addSecondMeter() {
        return tx.execute(status -> {
            Company company = em.find(Company.class, companyId);
            Asset asset = em.find(Asset.class, assetId);
            Meter meter = new Meter(); meter.setCompany(company); meter.setName("Temperature"); meter.setUnit("C");
            meter.setAsset(asset); meter.setUpdateFrequency(1); em.persist(meter);
            WorkOrderMeterTrigger rule = new WorkOrderMeterTrigger(); rule.setCompany(company); rule.setMeter(meter); rule.setAsset(asset);
            rule.setName("High temperature"); rule.setTitle("Inspect temperature"); rule.setTriggerCondition(WorkOrderMeterTriggerCondition.MORE_THAN);
            rule.setValue(10); rule.setEpisodePolicy(MeterEpisodePolicy.PER_EPISODE); em.persist(rule);
            em.flush(); em.clear(); return meter.getId();
        });
    }

    @Test @Order(12)
    void mybatisLedgerMatchesJpaOnTheSelectedDatabase() {
        var query=new com.grash.dto.query.LedgerQuery();query.setStart(Instant.now().minusSeconds(86400));query.setEnd(Instant.now().plusSeconds(86400));query.setSize(1);
        var scope=new com.grash.dto.query.AuthorizedQueryScope(actorId,companyId);
        for(int page=0;page<3;page++) {
            query.setPage(page);var expected=ledgerBaseline.search(scope,query);
            var rows=ledgerMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page+1L,1,false),companyId,query);
            assertEquals(expected.content(),rows);assertEquals(expected.total(),ledgerMapper.count(companyId,query));
        }
        assertTrue(ledgerMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1,10,false),-1,query).isEmpty());
    }

    @Test @Order(13)
    void replayAndMybatisAuditCommitAndRollbackTogether() {
        UUID eventId=UUID.randomUUID();
        tx.executeWithoutResult(status->eventStore.append(new EventEnvelope(1,eventId,companyId,"WORK_ORDER_CREATED",firstWorkOrderId,actorId,Instant.now(),List.of(),List.of(),Map.of())));
        jdbc.update("UPDATE event_retry SET state='DEAD' WHERE company_id=? AND event_id=?",companyId,eventId);
        long outboxBefore=companyCount("domain_event_outbox");
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{
            replay.replayEvent(actor,eventId,"Synthetic rollback proof");throw new IllegalStateException("rollback probe");
        }));
        assertEquals(0,companyCount("event_replay_audit"));assertEquals(outboxBefore,companyCount("domain_event_outbox"));
        replay.replayEvent(actor,eventId,"Synthetic recovery proof");
        assertEquals(1,companyCount("event_replay_audit"));assertEquals(outboxBefore+1,companyCount("domain_event_outbox"));
        var wrapper=new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.grash.writemodel.NotificationReplayAudit>()
                .eq(com.grash.writemodel.NotificationReplayAudit::getCompanyId,companyId);
        var audit=replayAudits.selectOne(wrapper);assertEquals(eventId,audit.getResourceId());assertEquals(actorId,audit.getActorId());
        assertThrows(CustomException.class,()->replay.replayEvent(actor,eventId,"Duplicate pending replay"));
        assertEquals(1,companyCount("event_replay_audit"));
    }

    @Test @Order(14)
    void sessionUuidPersistenceAndRevocationWorkOnTheSelectedDatabase() {
        UUID id=sessions.create(actorId,Date.from(Instant.now().plusSeconds(3600)));
        sessions.assertActive(id,actorId);
        assertTrue(sessions.list(actorId,id).stream().anyMatch(row->row.id().equals(id)&&row.current()));
        sessions.revoke(id,actorId);assertThrows(CustomException.class,()->sessions.assertActive(id,actorId));
    }

    @Test @Order(15)
    void nativeAnalyticsSqlIsExecutableWithTenantScope() {
        Date start=Date.from(Instant.now().minusSeconds(86400)),end=Date.from(Instant.now().plusSeconds(86400));
        assertNotNull(orderRepository.findTopNAssetsByIncompleteWO(companyId,start,end,5));
        assertNotNull(orderRepository.findTopNAssetsTimeCost(companyId,start,end,5));
        assertNotNull(orderRepository.findWOCostsByDateRange(companyId,start,end));
        assertNotNull(orderRepository.findTopNAssetsRepairTime(companyId,start,end,5));
        assertNotNull(orderRepository.findTotalWOCosts(companyId,start,end));
        assertNotNull(downtimeRepository.findTopNAssetsByDowntime(companyId,start,end,5));
        assertNotNull(downtimeRepository.findTopNAssetsForMTBF(companyId,start,end,5));
        assertNotNull(downtimeRepository.findTopNAssetsDowntimeAndCosts(companyId,start,end,5));
    }

    @Test @Order(16)
    void hierarchyCycleCannotCommit() {
        assertThrows(RuntimeException.class,()->tx.executeWithoutResult(status->{
            Asset a=em.find(Asset.class,assetId);a.setParentAsset(a);em.flush();
        }));
        assertNull(jdbc.queryForObject("SELECT parent_asset_id FROM asset WHERE id=?",Long.class,assetId));
    }

    private void raceMeterCommands(Long secondMeterId, boolean preloadSequence) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2); CyclicBarrier gate = new CyclicBarrier(2);
        try {
            Future<Reading> a = workers.submit(() -> concurrentMeterReading(meterId, "jpa-meter-one", gate, preloadSequence));
            Future<Reading> b = workers.submit(() -> concurrentMeterReading(secondMeterId, "jpa-meter-two", gate, preloadSequence));
            assertNotEquals(a.get(25, TimeUnit.SECONDS).getId(), b.get(25, TimeUnit.SECONDS).getId());
        } finally { workers.shutdownNow(); }
    }
    private Reading concurrentMeterReading(Long targetMeterId, String key, CyclicBarrier gate, boolean preloadSequence) {
        User fresh = actors.load(actor); Helper.setCurrentUser(fresh);
        try {
            return tx.execute(status -> {
                if (preloadSequence) {
                    // Both persistence contexts deliberately cache the same old value before either obtains the company lock.
                    em.createQuery("select s from CustomSequence s where s.company.id=:company", CustomSequence.class)
                            .setParameter("company", companyId).getSingleResult();
                }
                try { gate.await(10, TimeUnit.SECONDS); }
                catch (Exception failure) { throw new IllegalStateException(failure); }
                Meter meter = new Meter(); meter.setId(targetMeterId);
                Reading request = new Reading(); request.setMeter(meter); request.setValue(15);
                return readings.create(request, fresh, key);
            });
        } finally { SecurityContextHolder.clearContext(); }
    }
    private void assertDistinctGeneratedWorkOrderNumbers() {
        List<String> ids = jdbc.queryForList("SELECT w.custom_id FROM work_order w JOIN meter_trigger_execution x ON x.work_order_id=w.id WHERE x.company_id=?",
                String.class, companyId);
        assertEquals(2, ids.size()); assertEquals(2, new HashSet<>(ids).size());
        assertEquals(Set.of("WO000001", "WO000002"), new HashSet<>(ids));
    }
    @Test @Order(17)
    void jdbcNotificationIdBlocksDoNotOverlapHibernatePooledIds() {
        Set<Long> allocated=new HashSet<>();
        for(int round=0;round<3;round++) {
            long direct=tx.execute(status->database.nextNotificationId());
            assertTrue(allocated.add(direct));
            List<Long> orm=tx.execute(status->{
                List<Long> ids=new ArrayList<>();
                for(int i=0;i<60;i++) {
                    Notification notification=new Notification("Synthetic ID allocation",em.getReference(User.class,actorId),NotificationType.WORK_ORDER,firstWorkOrderId);
                    em.persist(notification);ids.add(notification.getId());
                }
                em.flush();return ids;
            });
            for(Long id:orm)assertTrue(allocated.add(id),"JDBC and Hibernate must reserve disjoint blocks");
        }
        assertEquals(183,allocated.size());
    }

    private DispatchMessage latestMeterDispatch() {
        String dispatch = jdbc.queryForObject(database.sql("""
                SELECT o.dispatch FROM domain_event_outbox o JOIN event_retry r ON r.id=o.retry_id
                WHERE o.company_id=? AND r.envelope::jsonb->>'eventType'='METER_EPISODE_OPENED'
                ORDER BY o.created_at DESC LIMIT 1
                ""","""
                SELECT o.dispatch FROM domain_event_outbox o JOIN event_retry r ON r.id=o.retry_id
                WHERE o.company_id=? AND JSON_UNQUOTE(JSON_EXTRACT(r.envelope,'$.eventType'))='METER_EPISODE_OPENED'
                ORDER BY o.created_at DESC LIMIT 1
                """), String.class, companyId);
        return eventJson.read(dispatch, DispatchMessage.class);
    }
    private int notificationCount(UUID eventId, Long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM notification WHERE event_company_id=? AND event_id=? AND user_id=?",
                Integer.class, companyId, eventId, userId);
    }

    @Test @Order(19)
    void metricsScrapesAndBusinessQuotaAreIsolatedOnTheRealServer() {
        var metrics = http.withBasicAuth("cmms-metrics", "local-metrics-isolation-test");
        assertEquals(401, http.getForEntity("/auth/me", String.class).getStatusCode().value());
        for (int i = 0; i < 5; i++) {
            assertEquals(200, metrics.getForEntity("/actuator/metrics", String.class).getStatusCode().value(),
                    "Authenticated scrapes must not consume anonymous business quota");
        }
        assertEquals(401, http.getForEntity("/auth/me", String.class).getStatusCode().value());
        assertEquals(429, http.getForEntity("/auth/me", String.class).getStatusCode().value(),
                "Business request rate limiting must remain active");
        assertEquals(200, metrics.getForEntity("/actuator/metrics", String.class).getStatusCode().value());
        assertEquals(401, http.getForEntity("/actuator/metrics", String.class).getStatusCode().value(),
                "Metrics must still require their dedicated credentials");
    }

    @Test @Order(18)
    void meterPolicyEditRoundTripsWithoutResettingOmittedPolicy() {
        com.grash.dto.WorkOrderMeterTriggerPatchDTO patch=tx.execute(status -> triggerMapper.toPatchDto(em.find(WorkOrderMeterTrigger.class,triggerId)));
        patch.setEpisodePolicy(MeterEpisodePolicy.ONCE);patch.setWaitBefore(60);
        var response=tx.execute(status -> triggerMapper.toShowDto(triggerService.update(triggerId,patch,actor.getCompany())));
        assertEquals(MeterEpisodePolicy.ONCE,response.getEpisodePolicy());assertEquals(60,response.getWaitBefore());
        patch.setEpisodePolicy(null);patch.setWaitBefore(120);
        var preserved=tx.execute(status -> triggerMapper.toShowDto(triggerService.update(triggerId,patch,actor.getCompany())));
        assertEquals(MeterEpisodePolicy.ONCE,preserved.getEpisodePolicy());assertEquals(120,preserved.getWaitBefore());
        patch.setEpisodePolicy(MeterEpisodePolicy.PER_EPISODE);patch.setWaitBefore(-1);
        assertThrows(CustomException.class,()->triggerService.update(triggerId,patch,actor.getCompany()));
        tx.executeWithoutResult(status -> {
            var stored=em.find(WorkOrderMeterTrigger.class,triggerId);
            assertEquals(MeterEpisodePolicy.ONCE,stored.getEpisodePolicy());assertEquals(120,stored.getWaitBefore());
        });
    }

    private PartQuantity quantityRequest(Long workOrderId, double value) {
        Part part = new Part(); part.setId(partId);
        WorkOrder order = new WorkOrder(); order.setId(workOrderId);
        return new PartQuantity(part, order, null, value);
    }
    private boolean consumeConcurrently(Long workOrderId, String key, CyclicBarrier gate) throws Exception {
        SecurityContextHolder.clearContext();
        User fresh = actors.load(actor); Helper.setCurrentUser(fresh);
        try {
            gate.await(10, TimeUnit.SECONDS);
            inventory.create(quantityRequest(workOrderId, 7), fresh, key);
            return true;
        } catch (CustomException expectedConflict) { return false; }
        finally { SecurityContextHolder.clearContext(); }
    }
    private Reading submit(double value, String key) {
        Meter meter = new Meter(); meter.setId(meterId);
        Reading reading = new Reading(); reading.setMeter(meter); reading.setValue(value);
        return readings.create(reading, actor, key);
    }
    private void ageLatestReading() {
        // Advance only synthetic sample dates to exercise multiple accepted cycles without weakening production frequency checks.
        jdbc.update(database.sql("UPDATE reading SET created_at=CURRENT_TIMESTAMP-INTERVAL '2 days' WHERE meter_id=?","UPDATE reading SET created_at=TIMESTAMPADD(DAY,-2,CURRENT_TIMESTAMP) WHERE meter_id=?"), meterId);
    }
    private double stock() { return jdbc.queryForObject("SELECT quantity FROM part WHERE id=?", Double.class, partId); }
    private long companyCount(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE company_id=?", Long.class, companyId); }
}
