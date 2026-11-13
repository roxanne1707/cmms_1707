package com.grash.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grash.dto.query.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssetOverviewCacheTest {
    private OverviewQuery query(){OverviewQuery q=new OverviewQuery();q.setStart(Instant.parse("2026-01-01T00:00:00Z"));q.setEnd(Instant.parse("2026-02-01T00:00:00Z"));return q;}
    private AssetWorkOrderOverview value(){OverviewQuery q=query();return new AssetWorkOrderOverview(AssetWorkOrderOverview.METRIC_VERSION,10,100,7,q.getStart(),q.getEnd(),true,false,Instant.now(),2,1,new BigDecimal("0.5"),new BigDecimal("72000"),1,0);}
    @Test void hitDoesNotRecompute()throws Exception{
        StringRedisTemplate redis=mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String,String> values=mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);ObjectMapper json=new ObjectMapper().findAndRegisterModules();
        AssetWorkOrderOverview expected=value();when(values.get(anyString())).thenReturn(json.writeValueAsString(expected));
        var actual=new AssetOverviewCache(redis,json).getOrLoad(new AuthorizedQueryScope(1,10),100,7,query(),()->{throw new AssertionError("cache hit");});
        assertEquals(expected,actual);verify(values,never()).setIfAbsent(anyString(),anyString(),any(Duration.class));
    }
    @Test void redisFailureFallsBackAndDoesNotSwallowDatabaseFailure(){
        StringRedisTemplate redis=mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new DataAccessResourceFailureException("offline"));
        AssetOverviewCache cache=new AssetOverviewCache(redis,new ObjectMapper().findAndRegisterModules());
        AtomicInteger calls=new AtomicInteger();
        AssetWorkOrderOverview expected=value();
        assertEquals(expected,cache.getOrLoad(new AuthorizedQueryScope(1,10),100,7,query(),()->{calls.incrementAndGet();return expected;}));
        assertEquals(1,calls.get());
        assertThrows(IllegalStateException.class,()->cache.getOrLoad(new AuthorizedQueryScope(1,10),100,7,query(),()->{throw new IllegalStateException("database");}));
        assertEquals(expected,cache.getOrLoad(new AuthorizedQueryScope(1,10),100,7,query(),()->expected));
    }
    @Test void corruptOrWrongEpochPayloadIsAMiss()throws Exception{
        StringRedisTemplate redis=mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String,String> values=mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);when(values.get(anyString())).thenReturn("{bad json");
        when(values.setIfAbsent(anyString(),anyString(),any(Duration.class))).thenReturn(true);
        AssetWorkOrderOverview expected=value();
        assertEquals(expected,new AssetOverviewCache(redis,new ObjectMapper().findAndRegisterModules())
                .getOrLoad(new AuthorizedQueryScope(1,10),100,7,query(),()->expected));
        verify(values).set(anyString(),anyString(),any(Duration.class));
    }
    @Test void exactTimeAndScopeAndEpochProduceDifferentKeys(){
        var scope=new AuthorizedQueryScope(1,10);OverviewQuery q=query();
        String first=AssetOverviewCache.key(scope,100,7,q);
        q.setStart(q.getStart().plusMillis(1));assertNotEquals(first,AssetOverviewCache.key(scope,100,7,q));
        assertNotEquals(first,AssetOverviewCache.key(scope,100,8,query()));
        assertNotEquals(first,AssetOverviewCache.key(new AuthorizedQueryScope(2,10),100,7,query()));
    }
}
