package com.grash.integration.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grash.cache.AssetOverviewCache;
import com.grash.configuration.MybatisReadConfig;
import com.grash.dto.query.*;
import com.grash.model.enums.Status;
import com.grash.service.query.*;
import jakarta.persistence.EntityManagerFactory;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in only. Never reads the application's deployed datasource configuration. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named="cmms.test.jdbc-url",matches=".+")
class QueryInfrastructureIntegrationTest {
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc,admin;
    private String schema;
    private com.grash.integration.JdbcLab lab;
    private EntityManagerFactory emf;
    private SqlSessionFactory sqlSessionFactory;
    private LettuceConnectionFactory redisFactory;
    private StringRedisTemplate redis;
    private AssetOverviewCache cache;
    private AssetWorkOrderOverviewRepository overview;
    private final AtomicLong users=new AtomicLong(System.currentTimeMillis()*1000);
    private final Set<String> cacheKeys=new HashSet<>();

    @BeforeAll void infrastructure()throws Exception{
        lab=new com.grash.integration.JdbcLab();schema=lab.schema;dataSource=lab.source;jdbc=lab.jdbc;
        jdbc.execute("CREATE TABLE company(id bigint PRIMARY KEY)");
        jdbc.execute("CREATE TABLE asset(id bigint PRIMARY KEY,company_id bigint NOT NULL REFERENCES company(id),name varchar(100))");
        jdbc.execute("""
            CREATE TABLE work_order(id bigint PRIMARY KEY,company_id bigint NOT NULL REFERENCES company(id),
             asset_id bigint REFERENCES asset(id) ON DELETE SET NULL,custom_id varchar(30),title varchar(100),
             status integer,priority integer,created_at timestamp NOT NULL,completed_on timestamp,
             archived boolean NOT NULL DEFAULT false,is_demo boolean DEFAULT false)
            """);
        jdbc.execute("CREATE TABLE meter_trigger_execution(company_id bigint NOT NULL,work_order_id bigint UNIQUE,trigger_id bigint,episode bigint,FOREIGN KEY(work_order_id) REFERENCES work_order(id) ON DELETE CASCADE)");
        if(lab.mysql) {
            lab.mysqlTables("asset_summary_epoch");
            String[] triggers=com.grash.integration.JdbcLab.resource("db/mysql/triggers.sql").split("-- end-trigger");
            for(int i=0;i<5;i++)jdbc.execute(triggers[i]);
            for(String sql:com.grash.integration.JdbcLab.resource("db/mysql/epoch-counter.sql").split(";"))if(!sql.isBlank())jdbc.execute(sql);
            for(String sql:com.grash.integration.JdbcLab.resource("db/mysql/epoch-counter-triggers.sql").split("-- end-trigger"))if(!sql.isBlank())jdbc.execute(sql);
            jdbc.execute(com.grash.integration.JdbcLab.resource("db/mysql/overview-index.sql"));
        } else jdbc.execute(new ClassPathResource("db/changelog/sql/asset_summary_epoch.sql").getContentAsString(StandardCharsets.UTF_8));
        jdbc.update("INSERT INTO company VALUES(11),(22)");
        jdbc.update("INSERT INTO asset VALUES(101,11,'Pump'),(102,11,'Motor'),(201,22,'Foreign')");
        LocalContainerEntityManagerFactoryBean factory=new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);factory.setPackagesToScan("queryfixture");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none","hibernate.jdbc.time_zone","UTC",
                "hibernate.cache.use_second_level_cache","false"));
        factory.afterPropertiesSet();emf=factory.getObject();
        sqlSessionFactory=new MybatisReadConfig().querySqlSessionFactory(dataSource);
        redisFactory=redisFactory(Integer.parseInt(System.getProperty("cmms.test.redis-port","26379")));
        redis=new StringRedisTemplate(redisFactory);redis.afterPropertiesSet();
        try(var connection=redis.getConnectionFactory().getConnection()){assertEquals("PONG",connection.ping());}
        cache=new AssetOverviewCache(redis,new ObjectMapper().findAndRegisterModules());
        overview=new AssetWorkOrderOverviewRepository(emf,jdbc,lab.database);
    }
    private LettuceConnectionFactory redisFactory(int port){
        RedisStandaloneConfiguration configuration=new RedisStandaloneConfiguration(
                System.getProperty("cmms.test.redis-host","127.0.0.1"),port);
        Duration connectionBudget=port==1?Duration.ofMillis(300):Duration.ofSeconds(5);
        var client=LettuceClientConfiguration.builder().commandTimeout(connectionBudget)
                .shutdownTimeout(Duration.ZERO).clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder().connectTimeout(connectionBudget).build()).build()).build();
        LettuceConnectionFactory factory=new LettuceConnectionFactory(configuration,client);
        factory.afterPropertiesSet();factory.start();return factory;
    }
    @AfterAll void cleanup(){
        if(redis!=null&&!cacheKeys.isEmpty())redis.delete(cacheKeys);
        if(redisFactory!=null)redisFactory.destroy();
        if(emf!=null)emf.close();
        if(lab!=null)lab.close();
    }
    @BeforeEach void fixture(){
        jdbc.update("DELETE FROM work_order");
        jdbc.execute("""
          INSERT INTO work_order(id,company_id,asset_id,custom_id,title,status,priority,created_at,completed_on,archived,is_demo) VALUES
          (1,11,101,'WO1','Complete',3,3,'2026-01-01','2026-01-01 20:00:00',false,false),
          (2,11,101,'WO2','Open',0,1,'2026-01-01',null,false,false),
          (3,11,101,'WO3','Bad clock',3,2,'2026-01-01','2025-12-31 23:00:00',false,false),
          (4,11,null,'WO4','Unassigned asset',3,0,'2026-01-01','2026-01-01 02:00:00',false,false),
          (5,11,101,'WO5','Demo',3,0,'2026-01-01','2026-01-01 04:00:00',false,true),
          (6,11,101,'WO6','Archived',3,2,'2026-01-01','2026-01-01 01:00:00',true,false),
          (7,22,201,'WO7','Foreign',3,3,'2026-01-01','2026-01-01 03:00:00',false,false)
          """);
        jdbc.update("INSERT INTO meter_trigger_execution VALUES(11,1,901,1),(22,7,902,1)");
    }
    private OverviewQuery query(){
        OverviewQuery q=new OverviewQuery();q.setStart(Instant.parse("2026-01-01T00:00:00Z"));q.setEnd(Instant.parse("2026-02-01T00:00:00Z"));
        q.normalizeAndValidate();return q;
    }
    private LedgerQuery ledger(){
        LedgerQuery q=new LedgerQuery();q.setStart(query().getStart());q.setEnd(query().getEnd());q.setSize(2);return q;
    }
    private AuthorizedQueryScope scope(){return new AuthorizedQueryScope(users.incrementAndGet(),11);}
    private AssetWorkOrderOverview cached(AuthorizedQueryScope scope,long epoch,OverviewQuery q,java.util.function.Supplier<AssetWorkOrderOverview> loader){
        String key=AssetOverviewCache.key(scope,101,epoch,q);cacheKeys.add(key);cacheKeys.add(key+":lock");
        return cache.getOrLoad(scope,101,epoch,q,loader);
    }

    @Test void jpaAggregateHasExplicitSampleAndArchiveSemantics(){
        var scope=scope();var q=query();
        var value=overview.aggregate(scope,101,overview.epoch(11,101),q);
        assertEquals(4,value.total());assertEquals(3,value.complete());assertEquals(2,value.validCycleSamples());
        assertEquals(0,overview.aggregate(new AuthorizedQueryScope(1,22),101,0,q).total());
        assertEquals(1,value.invalidCycleSamples());assertEquals(0,value.averageCycleSeconds().compareTo(new java.math.BigDecimal("37800")));
        q.setIncludeArchived(false);assertEquals(3,overview.aggregate(scope,101,0,q).total());
        // Future completion values stay field-based, rather than silently changing as wall time advances.
        jdbc.update("UPDATE work_order SET completed_on='2030-01-01' WHERE id=1");
        assertEquals(1,overview.aggregate(scope,101,0,q).invalidCycleSamples());
        q.setStart(Instant.parse("2026-01-02T00:00:00Z"));
        var empty=overview.aggregate(scope,101,0,q);assertEquals(0,empty.total());assertNull(empty.completionRate());assertNull(empty.averageCycleSeconds());
    }

    @Test void mybatisMatchesJpaAndPreservesNullAssetsAndStablePagination(){
        var scope=scope();var q=ledger();
        var baseline=new MaintenanceLedgerJpaRepository(emf,lab.database);
        try(var session=sqlSessionFactory.openSession()){
            var mapper=session.getMapper(com.grash.readmodel.mapper.MaintenanceLedgerMapper.class);
            assertEquals(5,mapper.count(11,q));
            for(int page=0;page<4;page++){
                q.setPage(page);
                var expected=baseline.search(scope,q);
                var actual=mapper.selectPage(new Page<>(page+1L,2,false),11,q);
                assertEquals(expected.content(),actual);
                if(page==0){assertEquals(6,actual.get(0).getId());assertEquals(4,actual.get(1).getId());assertNull(actual.get(1).getAssetId());}
            }
            q.setPage(0);q.setStatus(Status.OPEN);assertEquals(1,mapper.count(11,q));
            assertEquals(Status.OPEN,mapper.selectPage(new Page<>(1,2,false),11,q).get(0).getStatus());
            q.setStatus(null);q.setMeterTriggered(true);assertEquals(1,mapper.count(11,q));
            assertTrue(mapper.selectPage(new Page<>(1,2,false),11,q).get(0).isMeterTriggered());
            q.setMeterTriggered(false);assertEquals(4,mapper.count(11,q));
            q.setMeterTriggered(null);q.setAssetId(201L);assertEquals(0,mapper.count(11,q));
        }
    }

    @Test void triggerCoversChangeMoveDeleteAndRollback(){
        long before=overview.epoch(11,101),other=overview.epoch(11,102);
        jdbc.update("UPDATE work_order SET title='Text only' WHERE id=1");assertEquals(before,overview.epoch(11,101));
        TransactionTemplate tx=new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        tx.executeWithoutResult(status->{jdbc.update("UPDATE work_order SET status=1 WHERE id=2");status.setRollbackOnly();});
        assertEquals(before,overview.epoch(11,101));
        jdbc.update("UPDATE work_order SET asset_id=102 WHERE id=2");
        assertTrue(overview.epoch(11,101)>before);assertTrue(overview.epoch(11,102)>other);
        long afterMove=overview.epoch(11,102);
        jdbc.update("DELETE FROM work_order WHERE id=2");assertTrue(overview.epoch(11,102)>afterMove);
        long afterDelete=overview.epoch(11,102);
        jdbc.update("INSERT INTO work_order(id,company_id,asset_id,created_at,status) VALUES(8,11,102,'2026-01-01',0)");
        assertTrue(overview.epoch(11,102)>afterDelete);
    }

    @Test void truncateHonorsDatabaseSpecificInvalidationContract(){
        long before=overview.epoch(11,101);
        if(lab.mysql) {
            // MySQL has no TRUNCATE row trigger. Operators must rotate the deployment namespace.
            // Exercise that contract only on this fresh owned schema, never on an application database.
            var scope=scope();var q=query();cached(scope,before,q,()->overview.aggregate(scope,101,before,q));
            try(var c=dataSource.getConnection();var st=c.createStatement()) {
                st.execute("SET FOREIGN_KEY_CHECKS=0");
                try {st.execute("TRUNCATE work_order");st.execute("TRUNCATE meter_trigger_execution");}
                finally {st.execute("SET FOREIGN_KEY_CHECKS=1");}
            } catch(java.sql.SQLException e){throw new RuntimeException(e);}
            assertEquals(before,overview.epoch(11,101),"MySQL does not fire DELETE triggers on TRUNCATE");
            var rotated=new AssetOverviewCache(redis,new ObjectMapper().findAndRegisterModules());
            org.springframework.test.util.ReflectionTestUtils.setField(rotated,"namespace",schema+"-restore");
            String newKey=schema+"-restore:"+AssetOverviewCache.key(scope,101,before,q);cacheKeys.add(newKey);cacheKeys.add(newKey+":lock");
            assertEquals(0,rotated.getOrLoad(scope,101,before,q,()->overview.aggregate(scope,101,before,q)).total());
            return;
        }
        jdbc.execute("TRUNCATE work_order CASCADE");
        assertTrue(overview.epoch(11,101)>before);
        assertEquals(0,overview.aggregate(scope(),101,overview.epoch(11,101),query()).total());
    }
    @Test void assetCompanyRoundTripAndRecreationNeverReuseVersions(){
        if(lab.mysql)assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='asset_summary_epoch'",Integer.class),"Existing summary foreign keys must remain intact");
        jdbc.update("INSERT INTO asset VALUES(301,11,'Moved asset')");
        long initial=overview.epoch(11,301);
        jdbc.update("UPDATE asset SET company_id=22 WHERE id=301");
        long moved=overview.epoch(22,301);assertTrue(moved>initial);
        jdbc.update("UPDATE asset SET company_id=11 WHERE id=301");
        long restored=overview.epoch(11,301);assertTrue(restored>moved);
        jdbc.update("DELETE FROM asset WHERE id=301");
        jdbc.update("INSERT INTO asset VALUES(301,11,'Recreated asset')");
        assertTrue(overview.epoch(11,301)>restored);
        jdbc.update("DELETE FROM asset WHERE id=301");
    }
    @Test void twoConcurrentWritersNeverReuseAnEpoch()throws Exception{
        long before=overview.epoch(11,101);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch firstUpdated=new CountDownLatch(1),releaseFirst=new CountDownLatch(1),secondStarted=new CountDownLatch(1);
        try{
            Future<?> first=pool.submit(()->{
                try(var connection=dataSource.getConnection()){
                    connection.setAutoCommit(false);
                    connection.createStatement().executeUpdate("UPDATE work_order SET status=1 WHERE id=1");
                    firstUpdated.countDown();if(!releaseFirst.await(5,TimeUnit.SECONDS))throw new AssertionError("barrier");
                    connection.commit();
                }catch(Exception e){throw new RuntimeException(e);}
            });
            assertTrue(firstUpdated.await(5,TimeUnit.SECONDS));
            Future<?> second=pool.submit(()->{
                try(var connection=dataSource.getConnection()){
                    connection.setAutoCommit(false);secondStarted.countDown();
                    connection.createStatement().executeUpdate("UPDATE work_order SET status=2 WHERE id=2");connection.commit();
                }catch(Exception e){throw new RuntimeException(e);}
            });
            assertTrue(secondStarted.await(5,TimeUnit.SECONDS));releaseFirst.countDown();
            first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);
            assertTrue(overview.epoch(11,101)>=before+2);
        }finally{releaseFirst.countDown();pool.shutdownNow();}
    }

    @Test void oldReadFillingAfterCommitCannotPoisonNewEpochCache(){
        var scope=scope();var q=query();long oldEpoch=overview.epoch(11,101);
        var old=overview.aggregate(scope,101,oldEpoch,q);
        jdbc.update("UPDATE work_order SET status=3,completed_on='2026-01-01 02:00:00' WHERE id=2");
        long currentEpoch=overview.epoch(11,101);
        var current=cached(scope,currentEpoch,q,()->overview.aggregate(scope,101,currentEpoch,q));
        assertEquals(4,current.complete());
        cached(scope,oldEpoch,q,()->old);
        assertEquals(current,cached(scope,currentEpoch,q,()->{throw new AssertionError("expected Redis hit");}));
        assertNotEquals(oldEpoch,currentEpoch);
    }

    @Test void luaReleaseDoesNotDeleteNewOwnerAndRedisCorruptionFallsBack(){
        var scope=scope();var q=query();long epoch=overview.epoch(11,101);
        String key=AssetOverviewCache.key(scope,101,epoch,q);cacheKeys.add(key);cacheKeys.add(key+":lock");
        redis.opsForValue().set(key+":lock","new-owner",Duration.ofSeconds(10));
        cache.release(key+":lock","old-owner");assertEquals("new-owner",redis.opsForValue().get(key+":lock"));
        cache.release(key+":lock","new-owner");assertNull(redis.opsForValue().get(key+":lock"));
        redis.opsForValue().set(key,"{broken",Duration.ofSeconds(10));
        var value=cached(scope,epoch,q,()->overview.aggregate(scope,101,epoch,q));
        assertEquals(4,value.total());assertEquals(value,cached(scope,epoch,q,()->{throw new AssertionError("expected hit");}));
    }

    @Test void twoIndependentCacheInstancesShareTheSameRedisValue(){
        var scope=scope();var q=query();long epoch=overview.epoch(11,101);
        var expected=cached(scope,epoch,q,()->overview.aggregate(scope,101,epoch,q));
        var second=new AssetOverviewCache(new StringRedisTemplate(redisFactory),new ObjectMapper().findAndRegisterModules());
        assertEquals(expected,second.getOrLoad(scope,101,epoch,q,()->{throw new AssertionError("shared Redis hit expected");}));
    }
    @Test void realRedisConnectionFailureUsesDatabaseFallback(){
        LettuceConnectionFactory unavailable=redisFactory(1);
        try{
            StringRedisTemplate offline=new StringRedisTemplate(unavailable);offline.afterPropertiesSet();
            AssetOverviewCache fallback=new AssetOverviewCache(offline,new ObjectMapper().findAndRegisterModules());
            var scope=scope();var q=query();long epoch=overview.epoch(11,101);
            assertEquals(4,fallback.getOrLoad(scope,101,epoch,q,()->overview.aggregate(scope,101,epoch,q)).total());
        }finally{unavailable.destroy();}
    }
}
