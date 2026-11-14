package com.grash.security.ratelimit;

import com.grash.model.Company;
import com.grash.model.User;
import com.grash.security.CustomUserDetail;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;

class SharedMaintenanceRateLimiterTest {
    static SharedRateLimitProperties properties() {
        var p=new SharedRateLimitProperties();p.setNamespace("test-only");p.setIdentitySecret("synthetic-unit-secret-at-least-32-characters");return p;
    }
    @Test void enablingRequiresPrivateSecretAndBoundedQuotas() {
        assertThrows(IllegalArgumentException.class,()->new SharedRateLimitProperties().validate());
        var p=properties();p.getSubject().setBurst(0);assertThrows(IllegalArgumentException.class,p::validate);
    }
    @Test void nonTargetRouteDoesNotContactRedis() {
        var redis=mock(StringRedisTemplate.class);var limiter=new SharedMaintenanceRateLimiter(redis,properties());
        assertTrue(limiter.evaluate(new MockHttpServletRequest("POST","/readings"),null).isEmpty());
        verify(redis,never()).execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(Object[].class));
    }
    @Test void anonymousRequestCreationRemainsSubjectToAuthentication() {
        var limiter=new SharedMaintenanceRateLimiter(mock(StringRedisTemplate.class),properties());
        assertTrue(limiter.evaluate(new MockHttpServletRequest("POST","/requests"),null).isEmpty());
    }
    @Test void redisErrorFailsClosedAndDoesNotExposeException() {
        var redis=mock(StringRedisTemplate.class);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(Object[].class))).thenThrow(new IllegalStateException("private address and credential"));
        var limiter=new SharedMaintenanceRateLimiter(redis,properties());var req=new MockHttpServletRequest("POST","/auth/signin");req.setRemoteAddr("192.0.2.1");
        var result=limiter.evaluate(req,null).orElseThrow();assertEquals(503,result.status());assertEquals(1,result.retryAfterSeconds());
        assertFalse(result.body().toString().contains("credential"));
    }
    @Test void canonicalNumericAddressesShareIdentityAndHostnamesAreRejected() {
        assertEquals(SharedMaintenanceRateLimiter.canonicalAddress("2001:db8::1"),SharedMaintenanceRateLimiter.canonicalAddress("2001:0db8:0:0:0:0:0:1"));
        assertEquals("192.0.2.1",SharedMaintenanceRateLimiter.canonicalAddress("::ffff:192.0.2.1"));
        assertEquals("unknown",SharedMaintenanceRateLimiter.canonicalAddress("localhost"));
    }
    @Test void subjectKeysUseServerIdentityAndDoNotContainRawIp() {
        var redis=mock(StringRedisTemplate.class);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(Object[].class))).thenAnswer(inv->{
            List<String> keys=inv.getArgument(1);assertEquals(1,keys.size());assertFalse(keys.get(0).contains("192.0.2.1"));assertFalse(keys.get(0).contains("203.0.113.99"));return List.of(1L,0L);
        });
        var limiter=new SharedMaintenanceRateLimiter(redis,properties());var req=new MockHttpServletRequest("POST","/auth/signin");
        req.setRemoteAddr("192.0.2.1");req.addHeader("X-Forwarded-For","203.0.113.99");assertTrue(limiter.evaluate(req,null).orElseThrow().allowed());
    }
    @Test void companyAndUserBudgetsAreInSameSlotAndIgnoreSubmittedTenant() {
        var redis=mock(StringRedisTemplate.class);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(Object[].class))).thenAnswer(inv->{
            List<String> keys=inv.getArgument(1);assertEquals(2,keys.size());assertTrue(keys.stream().allMatch(k->k.contains("{request:company:7}")));return List.of(0L,60L);
        });
        var limiter=new SharedMaintenanceRateLimiter(redis,properties());var req=new MockHttpServletRequest("POST","/requests");req.addParameter("companyId","99");
        User user=new User();user.setId(11L);Company c=new Company();c.setId(7L);user.setCompany(c);
        var auth=new UsernamePasswordAuthenticationToken(CustomUserDetail.builder().user(user).build(),null,List.of());
        assertEquals(429,limiter.evaluate(req,auth).orElseThrow().status());
    }
    @Test void licensedPortalQuotaCannotBecomeSuccessWhenRedisIsUnavailable() {
        var limiter=new SharedMaintenanceRateLimiter(mock(StringRedisTemplate.class),properties());
        assertEquals(503,assertThrows(RateLimitRejected.class,()->limiter.requirePortal(1,2)).decision().status());
        assertThrows(IllegalArgumentException.class,()->limiter.requirePortal(0,2));
    }
}
