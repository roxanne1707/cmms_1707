package com.grash.security.ratelimit;

import com.grash.model.Company;
import com.grash.model.User;
import com.grash.security.CustomUserDetail;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit container suite: never connects to pre-existing/local Redis services. */
@Testcontainers
class SharedRateLimitContainerIT {
    @Container static final GenericContainer<?> ownedRedis=new GenericContainer<>("redis:8.4.0-alpine").withExposedPorts(6379);
    LettuceConnectionFactory factory;StringRedisTemplate redis;SharedRateLimitProperties p;
    SharedMaintenanceRateLimiter first,second;
    @BeforeEach void setup() {
        factory=factory(ownedRedis.getHost(),ownedRedis.getMappedPort(6379));redis=new StringRedisTemplate(factory);redis.afterPropertiesSet();
        p=SharedMaintenanceRateLimiterTest.properties();p.setNamespace("rl-it-"+UUID.randomUUID().toString().replace("-",""));
        p.setSubject(new SharedRateLimitProperties.Quota(8,3600,8,3600));p.setOrganization(new SharedRateLimitProperties.Quota(40,3600,40,3600));
        p.setAnonymous(new SharedRateLimitProperties.Quota(3,3600,3,3600));
        first=new SharedMaintenanceRateLimiter(redis,p);second=new SharedMaintenanceRateLimiter(new StringRedisTemplate(factory),p);
    }
    LettuceConnectionFactory factory(String host,int port) {
        var c=LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).shutdownTimeout(Duration.ZERO)
                .clientOptions(ClientOptions.builder().socketOptions(SocketOptions.builder().connectTimeout(Duration.ofMillis(500)).build()).build()).build();
        var f=new LettuceConnectionFactory(new RedisStandaloneConfiguration(host,port),c);f.afterPropertiesSet();f.start();return f;
    }
    @AfterEach void cleanup() {if(factory!=null)factory.destroy();}
    RateLimitDecision submit(SharedMaintenanceRateLimiter limiter,long company,long user) {
        Company c=new Company();c.setId(company);User u=new User();u.setId(user);u.setCompany(c);
        var auth=new UsernamePasswordAuthenticationToken(CustomUserDetail.builder().user(u).build(),null,List.of());
        return limiter.evaluate(new MockHttpServletRequest("POST","/requests"),auth).orElseThrow();
    }
    @Test void twoInstancesAllowOnlyOneSharedBudgetUnderConcurrency() throws Exception {
        var pool=Executors.newFixedThreadPool(12);var start=new CountDownLatch(1);
        try {List<Future<RateLimitDecision>> results=new ArrayList<>();
            for(int i=0;i<60;i++){var instance=i%2==0?first:second;results.add(pool.submit(()->{start.await();return submit(instance,1,11);}));}
            start.countDown();int allowed=0;for(var f:results){var d=f.get(10,TimeUnit.SECONDS);if(d.allowed())allowed++;else assertEquals(429,d.status());}
            assertEquals(8,allowed);
            assertEquals(429,submit(new SharedMaintenanceRateLimiter(new StringRedisTemplate(factory),p),1,11).status());
            assertTrue(submit(first,2,11).allowed());assertTrue(submit(second,1,12).allowed());
        } finally {pool.shutdownNow();}
    }
    @Test void rejectionDoesNotSpendOtherUsersOrganizationBudget() {
        for(int i=0;i<8;i++)assertTrue(submit(first,1,11).allowed());
        for(int i=0;i<50;i++)assertEquals(429,submit(second,1,11).status());
        for(long user=12;user<16;user++)for(int i=0;i<8;i++)assertTrue(submit(first,1,user).allowed());
        assertEquals(429,submit(second,1,16).status());
    }
    @Test void untrustedForwardedHeaderCannotChangeAnonymousBudget() {
        for(int i=0;i<5;i++) {
            var req=new MockHttpServletRequest("POST","/auth/signin");req.setRemoteAddr("192.0.2.12");req.addHeader("X-Forwarded-For","203.0.113."+i);
            assertEquals(i<3?200:429,(i%2==0?first:second).evaluate(req,null).orElseThrow().status());
        }
    }
    @Test void trustedNumericAddressCanonicalizationCannotMultiplyQuota() {
        p.setTrustedProxies("10.0.0.0/8");var limiter=new SharedMaintenanceRateLimiter(redis,p);
        for(int i=0;i<4;i++) {
            var req=new MockHttpServletRequest("POST","/auth/signin");req.setRemoteAddr("10.0.0.2");req.addHeader("X-Forwarded-For",i%2==0?"2001:db8::1":"2001:0db8:0:0:0:0:0:1");
            assertEquals(i<3?200:429,limiter.evaluate(req,null).orElseThrow().status());
        }
    }
    @Test void keysHaveTtlAndNoScriptCacheIsRequired() throws Exception {
        assertTrue(submit(first,1,11).allowed());
        for(String key:Objects.requireNonNull(redis.keys(p.getNamespace()+":*"))) {
            long ttl=Objects.requireNonNull(redis.getExpire(key,TimeUnit.MILLISECONDS));assertTrue(ttl>0&&ttl<=3600000);
        }
        // This container belongs exclusively to this class, never an existing deployment.
        assertEquals(0,ownedRedis.execInContainer("redis-cli","SCRIPT","FLUSH").getExitCode());
        assertTrue(submit(second,1,11).allowed());
    }
    @Test void refillUsesRedisClockAndNeverMintsTokensOnBackwardStoredTime() {
        assertTrue(submit(first,1,11).allowed());
        String key=redis.keys(p.getNamespace()+":*user:*").iterator().next();
        redis.opsForHash().put(key,"tokens1","0");redis.opsForHash().put(key,"tokens2","0");
        redis.opsForHash().put(key,"at",Long.toString(System.currentTimeMillis()+3600000));
        assertEquals(429,submit(second,1,11).status());
        redis.opsForHash().put(key,"at",Long.toString(System.currentTimeMillis()-3600000));
        assertTrue(submit(second,1,11).allowed());
    }
    @Test void redisPauseReturnsBounded503AndResumePreservesBudget() throws Exception {
        for(int i=0;i<8;i++)assertTrue(submit(first,1,11).allowed());
        // Pause only the Testcontainers-owned server; no service discovery or host port is used.
        ownedRedis.execInContainer("redis-cli","CLIENT","PAUSE","1200","ALL");
        long start=System.nanoTime();assertEquals(503,submit(second,1,11).status());
        assertTrue(Duration.ofNanos(System.nanoTime()-start).toMillis()<2000);
        Thread.sleep(1400);assertEquals(429,submit(first,1,11).status());
    }
    @Test void badRedisTypeFailsClosedBeforeAnyKeyIsMutated() {
        String subject=p.getNamespace()+":rl:v1:{request:company:1}:user:"+first.identity("11");redis.opsForValue().set(subject,"broken",Duration.ofMinutes(1));
        assertEquals(503,submit(first,1,11).status());
        assertFalse(Boolean.TRUE.equals(redis.hasKey(p.getNamespace()+":rl:v1:{request:company:1}:all")));
    }
}
