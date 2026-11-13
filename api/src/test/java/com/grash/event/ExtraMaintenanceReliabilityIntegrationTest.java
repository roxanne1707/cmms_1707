package com.grash.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grash.service.MaintenanceScheduleReceipt;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Uses a random disposable schema on the explicitly supplied isolated PostgreSQL only. */
@EnabledIfSystemProperty(named="cmms.test.jdbc-url",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/cmms_enhancement_test.*")
class ExtraMaintenanceReliabilityIntegrationTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc,bootstrap;
    private String schema;
    private TransactionTemplate tx;
    private MaintenanceScheduleReceipt receipts;
    private ReportAttachmentStore attachments;
    private EventStore events;
    @Configuration @EnableTransactionManagement static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void setup() {
        String url=System.getProperty("cmms.test.jdbc-url"),user=System.getProperty("cmms.test.jdbc-user","cmms_test"),password=System.getProperty("cmms.test.jdbc-password","cmms_test_local");
        bootstrap=new JdbcTemplate(new DriverManagerDataSource(url,user,password));schema="maintenance_extra_"+UUID.randomUUID().toString().replace("-","");
        bootstrap.execute("CREATE SCHEMA "+schema);
        DataSource source=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,password);
        jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE company(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE own_user(id BIGINT PRIMARY KEY,company_id BIGINT REFERENCES company(id))");
        jdbc.execute("CREATE TABLE notification(id BIGINT PRIMARY KEY,user_id BIGINT)");
        jdbc.execute("CREATE TABLE generated_order(id BIGINT PRIMARY KEY,company_id BIGINT)");
        new ResourceDatabasePopulator(new ClassPathResource("db/changelog/2026_11_14_reliable_events.sql"),new ClassPathResource("db/changelog/2026_11_14_reliability_completion.sql")).execute(source);
        jdbc.update("INSERT INTO company VALUES(1),(2)");
        context=new AnnotationConfigApplicationContext();context.register(com.grash.database.DatabasePlatform.class,Transactions.class);
        context.registerBean(DataSource.class,()->source);context.registerBean(JdbcTemplate.class,()->jdbc);
        context.registerBean(ObjectMapper.class,()->new ObjectMapper().findAndRegisterModules());
        context.registerBean(ReliableEventProperties.class,ReliableEventProperties::new);
        context.register(MaintenanceScheduleReceipt.class,ReportAttachmentStore.class,EventJson.class,EventStore.class);context.refresh();
        tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        receipts=context.getBean(MaintenanceScheduleReceipt.class);attachments=context.getBean(ReportAttachmentStore.class);events=context.getBean(EventStore.class);
    }
    @AfterEach void cleanup() {
        if(context!=null)context.close();
        if(bootstrap!=null && schema!=null && schema.matches("maintenance_extra_[a-f0-9]+"))bootstrap.execute("DROP SCHEMA "+schema+" CASCADE");
    }
    private EventEnvelope event(UUID id) {return new EventEnvelope(1,id,1,"WORK_ORDER_REPORT",3,4L,Instant.now(),List.of(),List.of(),Map.of("workOrderId",3));}
    private int count(String table) {return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    @Test void simultaneousOccurrencesCommitOneBusinessEffectAndOneOutbox() throws Exception {
        Instant time=Instant.parse("2026-11-14T08:00:00Z");UUID id=UUID.randomUUID();var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<Boolean> run=()->{start.await();return tx.execute(status->{
            if(!receipts.claim(1,9,"CREATE",time)) return false;
            jdbc.update("INSERT INTO generated_order VALUES(3,1)");events.append(event(id));receipts.complete(1,9,"CREATE",time,3L);return true;
        });};
        try {Future<Boolean>a=pool.submit(run),b=pool.submit(run);start.countDown();assertNotEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));}
        finally {pool.shutdownNow();}
        assertEquals(1,count("maintenance_schedule_execution"));assertEquals(1,count("generated_order"));assertEquals(1,count("domain_event_outbox"));
        assertTrue(Boolean.TRUE.equals(tx.execute(status->receipts.claim(1,9,"CREATE",time.plusSeconds(86400)))));
        assertTrue(Boolean.TRUE.equals(tx.execute(status->receipts.claim(2,9,"CREATE",time))));
    }
    @Test void businessFailureRollsBackOccurrenceAttachmentAndOutboxThenRetrySucceeds() {
        Instant time=Instant.parse("2026-11-14T08:00:00Z");UUID id=UUID.randomUUID();
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{
            assertTrue(receipts.claim(1,9,"CREATE",time));jdbc.update("INSERT INTO generated_order VALUES(3,1)");
            attachments.save(1,id,"report.pdf","%PDF-test".getBytes());events.append(event(id));throw new IllegalStateException("fault after writes");
        }));
        for(String table:List.of("maintenance_schedule_execution","generated_order","event_report_attachment","domain_event_outbox","event_retry"))assertEquals(0,count(table),table);
        tx.executeWithoutResult(status->{assertTrue(receipts.claim(1,9,"CREATE",time));attachments.save(1,id,"report.pdf","%PDF-test".getBytes());events.append(event(id));});
        assertEquals(1,count("event_report_attachment"));assertEquals(1,count("domain_event_outbox"));
    }
    @Test void reportSnapshotIsTenantBoundAndNotEmbeddedInDispatch() {
        UUID id=UUID.randomUUID();byte[] content="%PDF-private-snapshot".getBytes();
        tx.executeWithoutResult(status->{attachments.save(1,id,"report.pdf",content);events.append(event(id));});
        assertArrayEquals(content,attachments.require(1,id).content());
        assertThrows(IllegalStateException.class,()->attachments.require(2,id));
        assertFalse(jdbc.queryForObject("SELECT dispatch FROM domain_event_outbox",String.class).contains("private-snapshot"));
    }
    @Test void retentionRemovesOnlyCompletedOldReportsAndKeepsFailedDeliveryRecoverable() {
        UUID done=UUID.randomUUID(),dead=UUID.randomUUID(),unconsumed=UUID.randomUUID();
        tx.executeWithoutResult(status->{for(UUID id:List.of(done,dead,unconsumed)){attachments.save(1,id,"report.pdf",new byte[]{1});events.append(event(id));}});
        jdbc.update("UPDATE event_report_attachment SET created_at=CURRENT_TIMESTAMP-INTERVAL '91 days'");
        for(UUID id:List.of(done,dead)) {
            jdbc.update("INSERT INTO consumer_receipt(company_id,event_id,handler) VALUES(?,?,?)",1,id,EventEnvelope.HANDLER);
            jdbc.update("UPDATE event_retry SET state='DONE' WHERE event_id=?",id);
            jdbc.update("INSERT INTO event_delivery(id,company_id,event_id,channel,destination_id,purpose,payload,state) VALUES(?,1,?,'EMAIL','customer:1','WORK_ORDER_REPORT','{}',?)",UUID.randomUUID(),id,id.equals(done)?"SENT":"DEAD");
        }
        assertEquals(1,attachments.purgeCompletedOlderThan90Days());
        assertThrows(IllegalStateException.class,()->attachments.require(1,done));
        assertNotNull(attachments.require(1,dead));assertNotNull(attachments.require(1,unconsumed));
    }
}
