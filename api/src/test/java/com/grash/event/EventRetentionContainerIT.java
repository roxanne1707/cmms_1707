package com.grash.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grash.database.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Owns every database it touches. No connection to an existing application datasource. */
@Testcontainers
class EventRetentionContainerIT {
    @Container static final GenericContainer<?> rabbit=new GenericContainer<>("rabbitmq:4.4.0-alpine")
        .withEnv("RABBITMQ_DEFAULT_USER","retention_test").withEnv("RABBITMQ_DEFAULT_PASS","isolated-only")
        .withExposedPorts(5672);
    @Container static final PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final GenericContainer<?> mysql=new GenericContainer<>("mysql:8.0.33")
        .withEnv("MYSQL_ROOT_PASSWORD","isolated-retention-only").withEnv("MYSQL_ROOT_HOST","%")
        .withExposedPorts(3306).waitingFor(org.testcontainers.containers.wait.strategy.Wait.forLogMessage(".*ready for connections.*port: 3306.*\\n",1));
    @Configuration @EnableTransactionManagement static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}
    }
    class Fixture implements AutoCloseable {
        final AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext();
        final JdbcTemplate jdbc;
        final EventRetentionProperties settings=new EventRetentionProperties();
        final EventRetentionService retention;
        final EventStore store;
        final TransactionTemplate tx;
        final EventJson json=new EventJson(new ObjectMapper().findAndRegisterModules());
        Fixture(String engine) throws Exception {
            String name="retention_"+UUID.randomUUID().toString().replace("-","");DataSource source;
            if(engine.equals("mysql")) {
                String base="jdbc:mysql://"+mysql.getHost()+":"+mysql.getMappedPort(3306)+"/";
                // Intentionally differ from the case-sensitive event tables: new DDL must be explicit.
                new JdbcTemplate(new DriverManagerDataSource(base,"root","isolated-retention-only")).execute("CREATE DATABASE "+name+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
                source=new DriverManagerDataSource(base+name+"?useAffectedRows=true&connectionTimeZone=UTC","root","isolated-retention-only");
                jdbc=new MysqlJdbcConfiguration().jdbcTemplate(source);
            } else {
                new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword())).execute("CREATE SCHEMA "+name);
                source=new DriverManagerDataSource(pg.getJdbcUrl()+(pg.getJdbcUrl().contains("?")?"&":"?")+"currentSchema="+name,pg.getUsername(),pg.getPassword());jdbc=new JdbcTemplate(source);
            }
            jdbc.execute("CREATE TABLE company(id BIGINT PRIMARY KEY)");jdbc.execute("CREATE TABLE own_user(id BIGINT PRIMARY KEY)");
            if(engine.equals("postgresql")) {
                jdbc.execute("CREATE TABLE notification(id BIGINT PRIMARY KEY,user_id BIGINT)");
                new ResourceDatabasePopulator(new ClassPathResource("db/changelog/2026_11_14_reliable_events.sql")).execute(source);
            } else {
                String ddl=new String(new ClassPathResource("db/mysql/schema.sql").getInputStream().readAllBytes(),StandardCharsets.UTF_8).replaceAll("(?m)^--.*$","");
                Set<String> tables=Set.of("notification","notification_seq","consumer_receipt","domain_event_outbox","event_retry","event_delivery","event_replay_audit","event_quarantine","cmms_transaction_lock");
                var pattern=java.util.regex.Pattern.compile("(?is)^(?:CREATE TABLE|INSERT INTO|ALTER TABLE|CREATE (?:UNIQUE )?INDEX \\S+ ON)\\s+`?(\\w+)`?");
                for(String statement:ddl.split(";")){String sql=statement.trim();var match=pattern.matcher(sql);if(match.find()&&tables.contains(match.group(1)))jdbc.execute(sql);}
            }
            jdbc.execute("CREATE TABLE event_report_attachment(company_id BIGINT,event_id "+(engine.equals("mysql")?"CHAR(36)":"UUID")+")"+(engine.equals("mysql")?" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs":""));
            new ResourceDatabasePopulator(new ClassPathResource("db/changelog/sql/event_retention_"+engine+".sql")).execute(source);
            jdbc.update("INSERT INTO company VALUES(1),(2)");
            settings.setEnabled(true);settings.setDryRun(false);
            context.register(Transactions.class);context.registerBean(DataSource.class,()->source);context.registerBean(JdbcTemplate.class,()->jdbc);
            context.registerBean(DatabasePlatform.class,()->new DatabasePlatform(engine,jdbc));context.registerBean(EventJson.class,()->json);
            context.registerBean(EventRetentionProperties.class,()->settings);context.registerBean(ReliableEventProperties.class,ReliableEventProperties::new);
            context.register(EventStore.class,EventRetentionService.class);context.refresh();
            retention=context.getBean(EventRetentionService.class);store=context.getBean(EventStore.class);tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        }
        EventEnvelope old(long company) {
            var e=new EventEnvelope(1,UUID.randomUUID(),company,"WORK_ORDER_COMPLETED",123,null,Instant.now().minus(Duration.ofDays(120)),List.of(),List.of(),Map.of("private","synthetic-value"));
            tx.executeWithoutResult(s->store.append(e));Timestamp old=Timestamp.from(Instant.now().minus(Duration.ofDays(100)));
            jdbc.update("UPDATE event_retry SET state='DONE',updated_at=? WHERE event_id=?",old,e.eventId());
            jdbc.update("UPDATE domain_event_outbox SET status='PUBLISHED',published_at=? WHERE event_id=?",old,e.eventId());
            jdbc.update("INSERT INTO consumer_receipt(company_id,event_id,handler,completed_at) VALUES(?,?,?,?)",company,e.eventId(),EventEnvelope.HANDLER,old);
            jdbc.update("INSERT INTO event_delivery(id,company_id,event_id,channel,destination_id,purpose,payload,state,next_attempt_at) VALUES(?,?,?,'EMAIL','fixture','COMPLETED','{\"private\":\"synthetic\"}','SKIPPED',?)",UUID.randomUUID(),company,e.eventId(),old);
            return e;
        }
        int count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
        EventRetentionService.BatchResult run(boolean preview){return retention.run(1L,11L,preview,"Synthetic container verification");}
        public void close(){context.close();}
    }
    @ParameterizedTest @ValueSource(strings={"postgresql","mysql"}) void previewAndDefaultGates(String engine) throws Exception {
        try(var f=new Fixture(engine)){f.old(1);assertEquals(1,f.run(true).candidates());assertEquals(1,f.count("event_retry"));assertEquals(0,f.count("event_archive_summary"));
            f.settings.setEnabled(false);assertThrows(com.grash.exception.CustomException.class,()->f.run(false));
            f.settings.setEnabled(true);f.settings.setDryRun(true);assertThrows(com.grash.exception.CustomException.class,()->f.run(false));}
    }
    @ParameterizedTest @ValueSource(strings={"postgresql","mysql"}) void retiresOnlyPayloadsAndPreservesIdempotency(String engine) throws Exception {
        try(var f=new Fixture(engine)){var event=f.old(1);var result=f.run(false);assertEquals(1,result.archived());assertTrue(result.retiredPayloadBytes()>0);
            assertEquals(0,f.count("event_retry"));assertEquals(0,f.count("domain_event_outbox"));assertEquals(1,f.count("consumer_receipt"));assertEquals(1,f.count("event_delivery"));
            assertEquals("{}",f.jdbc.queryForObject("SELECT payload FROM event_delivery",String.class));
            f.tx.executeWithoutResult(s->f.store.append(event));assertEquals(0,f.count("event_retry"));
            assertTrue(f.store.claimAttempt(DispatchMessage.original(event)).isEmpty());
            assertThrows(IllegalArgumentException.class,()->f.store.claimAttempt(new DispatchMessage(1,UUID.randomUUID(),1,EventEnvelope.HANDLER,0,0)));
            assertEquals(0,f.run(false).archived());}
    }
    @ParameterizedTest @ValueSource(strings={"postgresql","mysql"}) void protectsIncompleteFailedRecentAndAttachedGroups(String engine) throws Exception {
        try(var f=new Fixture(engine)){
            var noReceipt=f.old(1);f.jdbc.update("DELETE FROM consumer_receipt WHERE event_id=?",noReceipt.eventId());
            var dead=f.old(1);f.jdbc.update("UPDATE event_delivery SET state='DEAD' WHERE event_id=?",dead.eventId());
            var pending=f.old(1);f.jdbc.update("UPDATE domain_event_outbox SET status='PENDING' WHERE event_id=?",pending.eventId());
            var processing=f.old(1);f.jdbc.update("UPDATE event_retry SET state='PROCESSING' WHERE event_id=?",processing.eventId());
            var recent=f.old(1);f.jdbc.update("UPDATE event_delivery SET next_attempt_at=CURRENT_TIMESTAMP(6) WHERE event_id=?",recent.eventId());
            var attached=f.old(1);f.jdbc.update("INSERT INTO event_report_attachment VALUES(?,?)",1,attached.eventId());
            var leased=f.old(1);f.jdbc.update("UPDATE domain_event_outbox SET claim_token=? WHERE event_id=?",UUID.randomUUID(),leased.eventId());
            assertEquals(0,f.run(false).candidates());assertEquals(7,f.count("event_retry"));assertEquals(7,f.count("event_delivery"));}
    }
    @ParameterizedTest @ValueSource(strings={"postgresql","mysql"}) void tenantScopeAndBatchBound(String engine) throws Exception {
        try(var f=new Fixture(engine)){f.old(1);f.old(1);f.old(2);f.settings.setBatchSize(1);assertEquals(1,f.run(false).archived());assertEquals(1,f.run(false).archived());assertEquals(0,f.run(false).archived());
            assertEquals(1,f.count("event_retry"));assertEquals(0,((List<?>)f.retention.status(2).get("archives")).size());}
    }
    @ParameterizedTest @ValueSource(strings={"postgresql","mysql"}) void concurrentWorkersHaveOneArchive(String engine) throws Exception {
        try(var f=new Fixture(engine)){f.old(1);var pool=Executors.newFixedThreadPool(2);try{
            var a=pool.submit(()->f.run(false));var b=pool.submit(()->f.run(false));assertEquals(1,a.get(20,TimeUnit.SECONDS).archived()+b.get(20,TimeUnit.SECONDS).archived());
            assertEquals(1,f.count("event_archive_summary"));assertEquals(1,f.count("consumer_receipt"));}finally{pool.shutdownNow();}}
    }
    @ParameterizedTest @ValueSource(strings={"postgresql","mysql"}) void failureRollsBackArchiveAndRemovalThenRecovers(String engine) throws Exception {
        try(var f=new Fixture(engine)){f.old(1);
            f.jdbc.execute("ALTER TABLE event_retry ADD CONSTRAINT block_retirement CHECK (1=1)");
            // A real FK rejects the DELETE after the archive INSERT, proving transaction rollback.
            f.jdbc.execute("CREATE TABLE cleanup_block(retry_id "+(engine.equals("mysql")?"CHAR(36)":"UUID")+" REFERENCES event_retry(id))"+(engine.equals("mysql")?" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs":""));
            if(engine.equals("mysql"))f.jdbc.execute("ALTER TABLE cleanup_block ADD FOREIGN KEY(retry_id) REFERENCES event_retry(id)");
            f.jdbc.update("INSERT INTO cleanup_block SELECT id FROM event_retry");
            assertEquals(1,f.run(false).failed());assertEquals(0,f.count("event_archive_summary"));assertEquals(1,f.count("domain_event_outbox"));assertEquals(1,f.count("event_retry"));
            f.jdbc.execute("DROP TABLE cleanup_block");assertEquals(1,f.run(false).archived());}
    }
    @ParameterizedTest @ValueSource(strings={"postgresql","mysql"}) void rowAndPayloadBudgetsSkipWithoutDataLoss(String engine) throws Exception {
        try(var f=new Fixture(engine)){f.old(1);f.settings.setMaxRowsPerEvent(2);assertEquals(1,f.run(false).skipped());assertEquals(1,f.count("event_retry"));
            f.settings.setMaxRowsPerEvent(2000);f.jdbc.update("UPDATE event_delivery SET payload=?","x".repeat(4096));f.settings.setMaxPayloadBytesPerEvent(1024);
            assertEquals(1,f.run(false).skipped());assertEquals(0,f.count("event_archive_summary"));}
    }
    @ParameterizedTest @ValueSource(strings={"postgresql","mysql"}) void lateBrokerDeliveryIsAckedWithoutRepeatingBusinessEffects(String engine) throws Exception {
        try(var f=new Fixture(engine)) {
            var event=f.old(1);assertEquals(1,f.run(false).archived());
            var factory=new com.rabbitmq.client.ConnectionFactory();factory.setHost(rabbit.getHost());factory.setPort(rabbit.getMappedPort(5672));
            factory.setUsername("retention_test");factory.setPassword("isolated-only");
            var notifications=org.mockito.Mockito.mock(NotificationCommandService.class);
            var listener=new WorkOrderEventListener(f.json,f.store,notifications,org.mockito.Mockito.mock(DatabaseConsumerBackoff.class));
            try(var connection=factory.newConnection();var channel=connection.createChannel()) {
                String queue=channel.queueDeclare().getQueue();
                for(var dispatch:List.of(DispatchMessage.original(event),new DispatchMessage(1,UUID.randomUUID(),1,EventEnvelope.HANDLER,0,0))) {
                    channel.basicPublish("",queue,null,f.json.write(dispatch).getBytes(StandardCharsets.UTF_8));
                    var received=channel.basicGet(queue,false);assertNotNull(received);
                    var properties=new org.springframework.amqp.core.MessageProperties();properties.setDeliveryTag(received.getEnvelope().getDeliveryTag());
                    listener.receive(new org.springframework.amqp.core.Message(received.getBody(),properties),channel);
                }
                assertEquals(0,channel.queueDeclarePassive(queue).getMessageCount());
            }
            org.mockito.Mockito.verifyNoInteractions(notifications);assertEquals(1,f.count("consumer_receipt"));assertEquals(1,f.count("event_quarantine"));
        }
    }
}
