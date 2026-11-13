package com.grash.service.query;

import com.grash.cache.AssetOverviewCache;
import com.grash.dto.query.*;
import com.grash.exception.CustomException;
import com.grash.readmodel.mapper.MaintenanceLedgerMapper;
import com.grash.security.QueryAccessPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QueryServicesTest {
    private OverviewQuery query(){OverviewQuery q=new OverviewQuery();q.setStart(Instant.parse("2026-01-01T00:00:00Z"));q.setEnd(Instant.parse("2026-02-01T00:00:00Z"));return q;}
    @Test void deniedRequestNeverTouchesEpochOrRedis(){
        QueryAccessPolicy policy=mock(QueryAccessPolicy.class);var repository=mock(AssetWorkOrderOverviewRepository.class);var cache=mock(AssetOverviewCache.class);
        when(policy.authorize(null,null,100L,false)).thenThrow(new CustomException("denied",HttpStatus.FORBIDDEN));
        assertThrows(CustomException.class,()->new AssetWorkOrderOverviewService(policy,repository,cache).overview(null,null,100,query()));
        verifyNoInteractions(repository,cache);
    }
    @Test void epochReadAlwaysPrecedesCachedResponse(){
        QueryAccessPolicy policy=mock(QueryAccessPolicy.class);var repository=mock(AssetWorkOrderOverviewRepository.class);var cache=mock(AssetOverviewCache.class);
        var scope=new AuthorizedQueryScope(1,10);when(policy.authorize(null,null,100L,false)).thenReturn(scope);
        when(repository.epoch(10,100)).thenReturn(8L);
        new AssetWorkOrderOverviewService(policy,repository,cache).overview(null,null,100,query());
        var order=inOrder(policy,repository,cache);order.verify(policy).authorize(null,null,100L,false);
        order.verify(repository).epoch(10,100);order.verify(cache).getOrLoad(eq(scope),eq(100L),eq(8L),any(),any());
    }

    @Test void noCacheBaselineStillAuthorizesAndReadsCurrentEpoch(){
        QueryAccessPolicy policy=mock(QueryAccessPolicy.class);var repository=mock(AssetWorkOrderOverviewRepository.class);var cache=mock(AssetOverviewCache.class);
        var scope=new AuthorizedQueryScope(1,10);when(policy.authorize(null,null,100L,false)).thenReturn(scope);
        when(repository.epoch(10,100)).thenReturn(9L);
        var service=new AssetWorkOrderOverviewService(policy,repository,cache);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"cacheEnabled",false);
        service.overview(null,null,100,query());
        var order=inOrder(policy,repository);order.verify(policy).authorize(null,null,100L,false);
        order.verify(repository).epoch(10,100);order.verify(repository).aggregate(eq(scope),eq(100L),eq(9L),any());
        verifyNoInteractions(cache);
    }

    @Test void ledgerPassesOnlyServerAuthorizedCompanyAndUsesExplicitCount(){
        var policy=mock(QueryAccessPolicy.class);var mapper=mock(MaintenanceLedgerMapper.class);
        when(policy.authorize(null,10L,null,true)).thenReturn(new AuthorizedQueryScope(1,10));
        LedgerQuery q=new LedgerQuery();q.setStart(query().getStart());q.setEnd(query().getEnd());q.setSize(10);q.setPage(1);
        when(mapper.count(10,q)).thenReturn(11L);when(mapper.selectPage(any(),eq(10L),same(q))).thenReturn(List.of());
        LedgerPage result=new MaintenanceLedgerService(policy,mapper).search(null,10L,q);
        assertEquals(11,result.total());assertEquals(1,result.page());
        verify(mapper).selectPage(argThat(page->page.getCurrent()==2 && page.getSize()==10 && !page.searchCount()),eq(10L),same(q));
    }
}
