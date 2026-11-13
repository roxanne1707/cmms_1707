package com.grash.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grash.exception.CustomException;
import com.grash.model.*;
import com.grash.model.Role;
import com.grash.model.enums.RoleCode;
import com.grash.repository.UserRepository;
import com.grash.service.BrandingService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.thymeleaf.spring6.SpringTemplateEngine;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Explicit isolated database only; never falls back to the application's datasource. */
@EnabledIfSystemProperty(named="cmms.test.jdbc-url",matches="jdbc:(postgresql://127\\.0\\.0\\.1:25432/cmms_enhancement_test.*|mysql://127\\.0\\.0\\.1:23306/cmms_mysql_test)")
class ReliableEventsPostgresIntegrationTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc,bootstrap;
    private String schema;
    private boolean mysql;
    private com.grash.database.DatabasePlatform platform;
    private EventStore store;
    private NotificationCommandService notifications;
    private EventDeliveryStore deliveries;
    private EventReplayService replay;
    private EventJson json;
    private EventAccessPolicy access;
    private ReliableEventProperties properties;
    private UserRepository users;
    private TransactionTemplate tx;
    @Configuration @EnableTransactionManagement static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void setup() {
        String url=System.getProperty("cmms.test.jdbc-url");
        String username=System.getProperty("cmms.test.jdbc-user","cmms_test");
        String password=System.getProperty("cmms.test.jdbc-password","cmms_test_local");
        bootstrap=new JdbcTemplate(new DriverManagerDataSource(url,username,password));
        mysql=url.startsWith("jdbc:mysql:");
        DataSource source;
        if(mysql) {
            schema="cmms_mysql_events_"+UUID.randomUUID().toString().replace("-","");
            bootstrap.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs");
            source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:23306/"+schema+"?useAffectedRows=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",username,password);
            jdbc=new com.grash.database.MysqlJdbcConfiguration().jdbcTemplate(source);
            jdbc.execute("CREATE TABLE company(id BIGINT PRIMARY KEY)");
            jdbc.execute("CREATE TABLE own_user(id BIGINT PRIMARY KEY,company_id BIGINT, FOREIGN KEY(company_id) REFERENCES company(id) ON DELETE CASCADE)");
            // Reuse the actual MySQL DDL for all event tables/constraints; only parent fixtures are minimal.
            try(var in=new ClassPathResource("db/mysql/schema.sql").getInputStream()) {
                String ddl=new String(in.readAllBytes(),StandardCharsets.UTF_8).replaceAll("(?m)^--.*$","");
                Set<String> tables=Set.of("notification","notification_seq","consumer_receipt","domain_event_outbox","event_retry","event_delivery","event_replay_audit","event_quarantine","cmms_transaction_lock");
                var tableName=java.util.regex.Pattern.compile("(?is)^(?:CREATE TABLE|INSERT INTO|ALTER TABLE|CREATE (?:UNIQUE )?INDEX \\S+ ON)\\s+`?(\\w+)`?");
                for(String statement:ddl.split(";")) {
                    String sql=statement.trim();var match=tableName.matcher(sql);
                    if(match.find() && tables.contains(match.group(1)))jdbc.execute(sql);
                }
            } catch(java.io.IOException ex){throw new IllegalStateException(ex);}
        } else {
        schema="events_it_"+UUID.randomUUID().toString().replace("-","");
        bootstrap.execute("CREATE SCHEMA "+schema);
        source=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,username,password);
        jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE company(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE own_user(id BIGINT PRIMARY KEY,company_id BIGINT REFERENCES company(id) ON DELETE CASCADE)");
        jdbc.execute("CREATE SEQUENCE notification_seq INCREMENT BY 50");
        jdbc.execute("CREATE TABLE notification(id BIGINT PRIMARY KEY,created_at TIMESTAMP NOT NULL,updated_at TIMESTAMP NOT NULL,created_by BIGINT,updated_by BIGINT,message VARCHAR(255) NOT NULL,notification_type INTEGER,resource_id BIGINT,seen BOOLEAN NOT NULL,user_id BIGINT REFERENCES own_user(id) ON DELETE CASCADE)");
        new ResourceDatabasePopulator(new ClassPathResource("db/changelog/2026_11_14_reliable_events.sql")).execute(source);
        }
        platform=new com.grash.database.DatabasePlatform(mysql?"mysql":"postgresql",jdbc);
        jdbc.update("INSERT INTO company VALUES(1),(2)");jdbc.update("INSERT INTO own_user VALUES(11,1),(12,1),(21,2)");
        context=new AnnotationConfigApplicationContext();
        context.register(com.grash.configuration.MybatisReadConfig.class,Transactions.class);
        context.registerBean(com.grash.database.DatabasePlatform.class,()->platform);
        context.registerBean(DataSource.class,()->source);
        context.registerBean(JdbcTemplate.class,()->jdbc);
        context.registerBean(ObjectMapper.class,()->new ObjectMapper().findAndRegisterModules());
        properties=new ReliableEventProperties();properties.setEnabled(true);
        context.registerBean(ReliableEventProperties.class,()->properties);
        access=mock(EventAccessPolicy.class);when(access.canReceive(any(),any())).thenReturn(true);when(access.canEmail(any(),any())).thenReturn(true);
        context.registerBean(EventAccessPolicy.class,()->access);
        context.registerBean(SimpMessageSendingOperations.class,()->mock(SimpMessageSendingOperations.class));
        users=mock(UserRepository.class);context.registerBean(UserRepository.class,()->users);
        context.register(EventJson.class,EventStore.class,NotificationCommandService.class,EventDeliveryStore.class,EventReplayService.class);
        context.refresh();
        store=context.getBean(EventStore.class);notifications=context.getBean(NotificationCommandService.class);
        deliveries=context.getBean(EventDeliveryStore.class);replay=context.getBean(EventReplayService.class);json=context.getBean(EventJson.class);
        tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }
    @AfterEach void cleanup() {
        if(context!=null) context.close();
        if(!mysql && bootstrap!=null && schema!=null && schema.matches("events_it_[a-f0-9]+")) bootstrap.execute("DROP SCHEMA "+schema+" CASCADE");
    }
    @Test void transactionRollbackLeavesNoEventAndNoAttempt() {
        EventEnvelope event=event(false);
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->{store.append(event);throw new IllegalStateException("rollback");}));
        assertEquals(0,count("domain_event_outbox"));assertEquals(0,count("event_retry"));
        tx.executeWithoutResult(s->store.append(event));assertEquals(1,count("domain_event_outbox"));
    }
    @Test void duplicateConsumptionHasOneReceiptNotificationAndDelivery() {
        EventEnvelope event=event(true);append(event);
        var attempt=store.claimAttempt(DispatchMessage.original(event)).orElseThrow();notifications.consume(attempt);
        assertTrue(store.claimAttempt(DispatchMessage.original(event)).isEmpty());
        assertEquals(1,count("consumer_receipt"));assertEquals(1,count("notification"));assertEquals(1,count("event_delivery"));
    }
    @Test void concurrentConsumersClaimOneDurableAttemptAndCommitOneNotification() throws Exception {
        EventEnvelope event=event(false);append(event);
        var start=new java.util.concurrent.CountDownLatch(1);
        var claimed=new java.util.concurrent.CountDownLatch(2);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<Boolean> consumer=()->{
            start.await();
            var attempt=store.claimAttempt(DispatchMessage.original(event));
            claimed.countDown();
            assertTrue(claimed.await(10,java.util.concurrent.TimeUnit.SECONDS));
            if(attempt.isPresent()) notifications.consume(attempt.get());
            return attempt.isPresent();
        };
        try {
            var first=pool.submit(consumer);var second=pool.submit(consumer);start.countDown();
            int owners=(first.get(15,java.util.concurrent.TimeUnit.SECONDS)?1:0)+(second.get(15,java.util.concurrent.TimeUnit.SECONDS)?1:0);
            assertEquals(1,owners);assertEquals(1,count("consumer_receipt"));assertEquals(1,count("notification"));
            assertEquals("DONE",jdbc.queryForObject("SELECT state FROM event_retry",String.class));
        } finally {pool.shutdownNow();}
    }
    @Test void stalePublicationAndConsumerTokensCannotCommit() {
        EventEnvelope event=event(false);append(event);
        var first=store.claimPublications().get(0);
        jdbc.update("UPDATE domain_event_outbox SET lease_until=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)));
        var second=store.claimPublications().get(0);
        assertFalse(store.publicationResult(first,true,null));assertTrue(store.publicationResult(second,true,null));
        var firstAttempt=store.claimAttempt(DispatchMessage.original(event)).orElseThrow();
        jdbc.update("UPDATE event_retry SET lease_until=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)));
        var secondAttempt=store.claimAttempt(DispatchMessage.original(event)).orElseThrow();
        assertThrows(IllegalStateException.class,()->notifications.consume(firstAttempt));
        assertEquals(0,count("consumer_receipt"));notifications.consume(secondAttempt);assertEquals(1,count("notification"));
    }
    @Test void failedMaterializationRollsBackAndOldAttemptCannotBypassBackoff() {
        EventEnvelope original=event(false);
        var secondTarget=new EventEnvelope.Target(12L,"second@example.invalid","ASSIGNMENT","second","work","WORK_ORDER",1,null,"en",Map.of(),false,false);
        EventEnvelope event=new EventEnvelope(1,original.eventId(),1,original.eventType(),1,null,original.occurredAt(),List.of(original.targets().get(0),secondTarget),List.of(),Map.of());
        append(event);var attempt=store.claimAttempt(DispatchMessage.original(event)).orElseThrow();
        when(access.canReceive(any(),any())).thenReturn(true).thenThrow(new IllegalStateException("failure after first insert"));
        assertThrows(IllegalStateException.class,()->notifications.consume(attempt));
        assertEquals(0,count("notification"));assertEquals(0,count("consumer_receipt"));
        store.failAttempt(attempt,new IllegalStateException("injected"),false);
        assertEquals(2,count("event_retry"));assertEquals(2,count("domain_event_outbox"));
        assertTrue(store.claimAttempt(DispatchMessage.original(event)).isEmpty());
        store.failAttempt(attempt,new IllegalStateException("duplicate"),false);assertEquals(2,count("event_retry"));
        DispatchMessage next=new DispatchMessage(1,event.eventId(),1,EventEnvelope.HANDLER,0,1);
        // Control the persisted clock boundary instead of racing the real one-second backoff on a busy CI host.
        jdbc.update("UPDATE event_retry SET next_attempt_at=? WHERE business_attempt=1",java.sql.Timestamp.from(Instant.now().plusSeconds(3600)));
        assertTrue(store.claimAttempt(next).isEmpty());
        jdbc.update("UPDATE event_retry SET next_attempt_at=? WHERE business_attempt=1",java.sql.Timestamp.from(Instant.now().minusSeconds(1)));
        doReturn(true).when(access).canReceive(any(),any());
        notifications.consume(store.claimAttempt(next).orElseThrow());assertEquals(2,count("notification"));
    }
    @Test void unsupportedStoredEnvelopeIsDeadLetteredInsteadOfStrandedAfterAck() {
        EventEnvelope event=event(false);append(event);
        jdbc.update("UPDATE event_retry SET envelope='{}'");
        assertTrue(store.claimAttempt(DispatchMessage.original(event)).isEmpty());
        assertEquals("DEAD",jdbc.queryForObject("SELECT state FROM event_retry",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM domain_event_outbox WHERE purpose='DEAD'",Integer.class));
        assertEquals(0,count("consumer_receipt"));
    }
    @Test void expiredConsumerLeaseIsDurablyRepublished() {
        EventEnvelope event=event(false);append(event);
        var publication=store.claimPublications().get(0);store.publicationResult(publication,true,null);
        store.claimAttempt(DispatchMessage.original(event)).orElseThrow();
        jdbc.update("UPDATE event_retry SET lease_until=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)));
        store.recoverExpiredAttempts();
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM domain_event_outbox",String.class));
        notifications.consume(store.claimAttempt(DispatchMessage.original(event)).orElseThrow());assertEquals(1,count("notification"));
    }
    @Test void deadEventReplayAndDeadDeliveryRetryRemainSeparateAndTenantScoped() {
        properties.setMaxAttempts(1);EventEnvelope event=event(true);append(event);
        var attempt=store.claimAttempt(DispatchMessage.original(event)).orElseThrow();store.failAttempt(attempt,new IllegalStateException(),false);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM domain_event_outbox WHERE purpose='DEAD'",Integer.class));
        User admin=admin(11,1);when(users.findByIdAndCompany_Id(11L,1L)).thenReturn(Optional.of(admin));
        replay.replayEvent(admin,event.eventId(),"Downstream configuration repaired");
        var replayMessage=new DispatchMessage(1,event.eventId(),1,EventEnvelope.HANDLER,1,0);
        notifications.consume(store.claimAttempt(replayMessage).orElseThrow());
        assertThrows(CustomException.class,()->replay.replayEvent(admin,event.eventId(),"Cannot repeat success"));
        UUID delivery=jdbc.queryForObject("SELECT id FROM event_delivery",UUID.class);
        jdbc.update("UPDATE event_delivery SET state='DEAD'");
        User other=admin(21,2);when(users.findByIdAndCompany_Id(21L,2L)).thenReturn(Optional.of(other));
        assertThrows(CustomException.class,()->replay.retryDelivery(other,delivery,"Other company attempted retry"));
        replay.retryDelivery(admin,delivery,"Mail endpoint configuration repaired");
        assertEquals(1,count("notification"));assertEquals(1,count("consumer_receipt"));
        assertEquals("READY",jdbc.queryForObject("SELECT state FROM event_delivery",String.class));
        assertEquals(2,count("event_replay_audit"));
    }
    @Test void failureListShowsOnlyCurrentActionableEventsAndKeepsTenantIsolation() {
        properties.setMaxAttempts(1);
        EventEnvelope event=event(false);append(event);
        store.failAttempt(store.claimAttempt(DispatchMessage.original(event)).orElseThrow(),new IllegalStateException("first failure"),false);
        User admin=admin(11,1);when(users.findByIdAndCompany_Id(11L,1L)).thenReturn(Optional.of(admin));
        User other=admin(21,2);when(users.findByIdAndCompany_Id(21L,2L)).thenReturn(Optional.of(other));
        assertEquals(1,((List<?>)replay.failures(admin).get("events")).size());
        assertTrue(((List<?>)replay.failures(other).get("events")).isEmpty());
        replay.replayEvent(admin,event.eventId(),"Repair confirmed; replay event");
        assertTrue(((List<?>)replay.failures(admin).get("events")).isEmpty(),"Historical DEAD must disappear while replay is READY");
        var second=new DispatchMessage(1,event.eventId(),1,EventEnvelope.HANDLER,1,0);
        store.failAttempt(store.claimAttempt(second).orElseThrow(),new IllegalStateException("second failure"),false);
        var current=(List<Map<String,Object>>)replay.failures(admin).get("events");
        assertEquals(1,current.size());assertEquals(1,((Number)current.get(0).get("replay_generation")).intValue());
        replay.replayEvent(admin,event.eventId(),"Recovered; final successful retry");
        notifications.consume(store.claimAttempt(new DispatchMessage(1,event.eventId(),1,EventEnvelope.HANDLER,2,0)).orElseThrow());
        assertTrue(((List<?>)replay.failures(admin).get("events")).isEmpty(),"Successful receipt must not leave a stale replay action");
    }
    @Test void deadEventGaugeCountsOnlyLatestUnconsumedAttempt() {
        var registry=new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        try {
            new EventMetrics(jdbc,platform).bindTo(registry);
            var gauge=registry.get("cmms.events.consumer.dead").gauge();
            properties.setMaxAttempts(1);EventEnvelope event=event(false);append(event);
            User admin=admin(11,1);when(users.findByIdAndCompany_Id(11L,1L)).thenReturn(Optional.of(admin));
            assertEquals(0d,gauge.value());
            assertTrue(Double.isFinite(registry.get("cmms.events.outbox.oldest.seconds").gauge().value()));
            store.failAttempt(store.claimAttempt(DispatchMessage.original(event)).orElseThrow(),new IllegalStateException("first failure"),false);
            assertEquals(1d,gauge.value());
            replay.replayEvent(admin,event.eventId(),"Repair and replay one");assertEquals(0d,gauge.value());
            var second=new DispatchMessage(1,event.eventId(),1,EventEnvelope.HANDLER,1,0);
            store.failAttempt(store.claimAttempt(second).orElseThrow(),new IllegalStateException("second failure"),false);
            assertEquals(1d,gauge.value(),"Two historical DEAD attempts represent one actionable event");
            replay.replayEvent(admin,event.eventId(),"Repair and replay two");
            notifications.consume(store.claimAttempt(new DispatchMessage(1,event.eventId(),1,EventEnvelope.HANDLER,2,0)).orElseThrow());
            assertEquals(0d,gauge.value());
            jdbc.update("UPDATE event_retry SET state='DEAD' WHERE replay_generation=2");
            assertEquals(0d,gauge.value(),"Committed receipt also excludes a terminal historical row");
        } finally { registry.close(); }
    }
    @Test void localHttpDeliveryRetriesWithStableIdentity() throws Exception {
        EventEnvelope event=event(true);append(event);notifications.consume(store.claimAttempt(DispatchMessage.original(event)).orElseThrow());
        AtomicInteger calls=new AtomicInteger();Set<String> ids=java.util.concurrent.ConcurrentHashMap.newKeySet();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/deliver",exchange->{ids.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));exchange.getRequestBody().readAllBytes();
            int code=calls.incrementAndGet()==1?503:200;exchange.sendResponseHeaders(code,0);exchange.getResponseBody().close();});server.start();
        try {
            properties.setLocalHttpSink("http://127.0.0.1:"+server.getAddress().getPort()+"/deliver");
            StandardEnvironment env=new StandardEnvironment();env.getPropertySources().addFirst(new MapPropertySource("test",Map.of("mail.enable",true)));
            EventDeliverySender sender=new EventDeliverySender(properties,access,json,env,mock(SpringTemplateEngine.class),mock(BrandingService.class),mock(JavaMailSender.class),mock(ReportAttachmentStore.class));
            EventDeliveryWorker worker=new EventDeliveryWorker(deliveries,sender,json);
            worker.deliverOne();assertEquals("RETRY",jdbc.queryForObject("SELECT state FROM event_delivery",String.class));
            jdbc.update("UPDATE event_delivery SET next_attempt_at=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)));worker.deliverOne();
            assertEquals("SENT",jdbc.queryForObject("SELECT state FROM event_delivery",String.class));
            assertEquals(2,calls.get());assertEquals(1,ids.size());assertEquals(1,count("notification"));
        } finally {server.stop(0);}
    }
    @Test void rabbitConfirmedPublishManualAckAndUnroutableReturn() throws Exception {
        String suffix=UUID.randomUUID().toString();properties.setExchange("cmms.events.it."+suffix);properties.setQueue("cmms.dispatch.it."+suffix);
        properties.setDeadExchange("cmms.dead.it."+suffix);properties.setDeadQueue("cmms.deadqueue.it."+suffix);
        CachingConnectionFactory factory=new CachingConnectionFactory("127.0.0.1",Integer.getInteger("cmms.test.rabbit-port",25672));
        factory.setUsername(System.getProperty("cmms.test.rabbit-user","guest"));factory.setPassword(System.getProperty("cmms.test.rabbit-password","guest"));
        RabbitAdmin admin=new RabbitAdmin(factory);
        EventRabbitConfiguration config=new EventRabbitConfiguration();RabbitTemplate template=config.eventRabbitTemplate(factory,properties);
        try {
            admin.declareExchange(new TopicExchange(properties.getExchange(),true,false));
            org.springframework.amqp.core.Queue queue=QueueBuilder.durable(properties.getQueue()).quorum().build();admin.declareQueue(queue);
            admin.declareBinding(BindingBuilder.bind(queue).to(new TopicExchange(properties.getExchange())).with("notification"));
            EventEnvelope event=event(false);append(event);
            OutboxPublisher publisher=new OutboxPublisher(store,template,properties);publisher.publishPending();
            await(()->"PUBLISHED".equals(jdbc.queryForObject("SELECT status FROM domain_event_outbox",String.class)));
            WorkOrderEventListener listener=new WorkOrderEventListener(json,store,notifications,mock(DatabaseConsumerBackoff.class));
            template.execute(channel->{var delivery=channel.basicGet(properties.getQueue(),false);assertNotNull(delivery);
                MessageProperties mp=new MessageProperties();mp.setDeliveryTag(delivery.getEnvelope().getDeliveryTag());listener.receive(new Message(delivery.getBody(),mp),channel);return null;});
            assertEquals(1,count("notification"));assertEquals(0,admin.getQueueInfo(properties.getQueue()).getMessageCount());
            template.send(properties.getQueue(),new Message(json.write(DispatchMessage.original(event)).getBytes(StandardCharsets.UTF_8)));
            await(()->admin.getQueueInfo(properties.getQueue()).getMessageCount()==1);
            template.execute(channel->{var delivery=channel.basicGet(properties.getQueue(),false);assertNotNull(delivery);
                MessageProperties mp=new MessageProperties();mp.setDeliveryTag(delivery.getEnvelope().getDeliveryTag());listener.receive(new Message(delivery.getBody(),mp),channel);return null;});
            assertEquals(1,count("notification"));
            admin.removeBinding(BindingBuilder.bind(queue).to(new TopicExchange(properties.getExchange())).with("notification"));
            EventEnvelope unroutable=event(false);append(unroutable);publisher.publishPending();
            await(()->Integer.valueOf(1).equals(jdbc.queryForObject("SELECT count(*) FROM domain_event_outbox WHERE last_error='UNROUTABLE' AND status='PENDING'",Integer.class)));
        } finally {admin.deleteQueue(properties.getQueue());admin.deleteExchange(properties.getExchange());factory.destroy();}
    }
    private EventEnvelope event(boolean email) {
        var target=new EventEnvelope.Target(11L,"test@example.invalid","ASSIGNMENT","Assigned a work order","New work","WORK_ORDER",1,
                "new-work-order.html","en",Map.of("workOrderTitle","Test","workOrderLink","http://localhost/work/1"),email,false);
        return new EventEnvelope(1,UUID.randomUUID(),1,"WORK_ORDER_CREATED",1,null,Instant.now(),List.of(target),List.of(),Map.of("workOrderId",1));
    }
    private void append(EventEnvelope event) {tx.executeWithoutResult(s->store.append(event));}
    private int count(String table) {return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    private User admin(long id,long companyId) {User user=new User();user.setId(id);Company company=new Company();company.setId(companyId);user.setCompany(company);user.setEnabled(true);Role role=new Role();role.setCode(RoleCode.ADMIN);user.setRole(role);return user;}
    private void await(BooleanSupplier condition) throws InterruptedException {long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);while(!condition.getAsBoolean() && System.nanoTime()<end) Thread.sleep(25);assertTrue(condition.getAsBoolean(),"Condition did not become true within 15 seconds");}
}
