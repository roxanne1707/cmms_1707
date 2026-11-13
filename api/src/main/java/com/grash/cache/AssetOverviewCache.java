package com.grash.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grash.dto.query.*;
import io.micrometer.core.instrument.Metrics;
import com.grash.exception.CustomException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Redis contains disposable DTOs. Locks only suppress rebuilds; PG versions provide freshness. */
@Component
@ConditionalOnProperty(name="enhancement.query.enabled", havingValue="true")
public class AssetOverviewCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final ConcurrentMap<String, CompletableFuture<AssetWorkOrderOverview>> flights = new ConcurrentHashMap<>();
    private final Semaphore rebuilds = new Semaphore(8);
    private final Semaphore flightSlots = new Semaphore(256);
    private final DefaultRedisScript<Long> unlock;

    public AssetOverviewCache(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis=redis; this.mapper=mapper;
        unlock = new DefaultRedisScript<>();
        unlock.setLocation(new ClassPathResource("redis/query-compare-delete.lua"));
        unlock.setResultType(Long.class);
    }

    public static String key(AuthorizedQueryScope scope, long assetId, long epoch, OverviewQuery q) {
        return "cmms:asset-wo:v1:{" + scope.companyId() + ":" + assetId + "}:e" + epoch
                + ":" + scope.cacheScope() + ":from" + q.getStart().toEpochMilli()
                + ":to" + q.getEnd().toEpochMilli() + ":a" + q.isIncludeArchived() + ":d" + q.isIncludeDemo();
    }

    public AssetWorkOrderOverview getOrLoad(AuthorizedQueryScope scope, long assetId, long epoch,
                                            OverviewQuery q, Supplier<AssetWorkOrderOverview> loader) {
        String key=key(scope,assetId,epoch,q);
        AssetWorkOrderOverview hit=read(key,scope,assetId,epoch,q);
        Metrics.counter("cmms.query.cache.requests","result",hit==null?"miss":"hit").increment();
        if (hit!=null) return hit;
        CompletableFuture<AssetWorkOrderOverview> mine=new CompletableFuture<>();
        // Bound key cardinality; the database semaphore remains authoritative if the map is full.
        if (!flightSlots.tryAcquire()) return rebuild(key,scope,assetId,epoch,q,loader);
        CompletableFuture<AssetWorkOrderOverview> existing=flights.putIfAbsent(key,mine);
        if (existing!=null) {
            flightSlots.release();
            try { return existing.get(4,TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw busy(); }
            catch (TimeoutException e) { throw busy(); }
            catch (ExecutionException e) {
                if (e.getCause() instanceof RuntimeException runtime) throw runtime;
                throw busy();
            }
        }
        try {
            AssetWorkOrderOverview result=rebuild(key,scope,assetId,epoch,q,loader);
            mine.complete(result);
            return result;
        } catch (RuntimeException e) { mine.completeExceptionally(e); throw e; }
        finally { flights.remove(key,mine); flightSlots.release(); }
    }

    private AssetWorkOrderOverview rebuild(String key, AuthorizedQueryScope scope, long assetId, long epoch,
                                            OverviewQuery q, Supplier<AssetWorkOrderOverview> loader) {
        String lockKey=key+":lock", token=UUID.randomUUID().toString();
        boolean locked=false, acquired=false;
        try {
            try {
                locked=Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey,token,Duration.ofSeconds(5)));
                if (!locked) {
                    // Bounded wait, then bounded fallback. A lease is never required for correctness.
                    for (int i=0;i<3;i++) {
                        Thread.sleep(25);
                        AssetWorkOrderOverview hit=read(key,scope,assetId,epoch,q);
                        if (hit!=null) return hit;
                    }
                }
            } catch (org.springframework.dao.DataAccessException ignored) { redisError("lock"); }
            acquired=rebuilds.tryAcquire(50,TimeUnit.MILLISECONDS);
            if (!acquired) throw busy();
            AssetWorkOrderOverview hit=read(key,scope,assetId,epoch,q);
            if (hit!=null) return hit;
            long started=System.nanoTime();
            AssetWorkOrderOverview result=Metrics.timer("cmms.query.rebuild").record(loader);
            long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
            long ttl=ThreadLocalRandom.current().nextLong(30000,60001)-elapsed;
            if (ttl>0) {
                try { redis.opsForValue().set(key,mapper.writeValueAsString(result),Duration.ofMillis(ttl)); }
                catch (org.springframework.dao.DataAccessException | JsonProcessingException ignored) { redisError("write"); }
            }
            return result;
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw busy(); }
        finally {
            if (acquired) rebuilds.release();
            if (locked) release(lockKey,token);
        }
    }

    private AssetWorkOrderOverview read(String key, AuthorizedQueryScope scope, long assetId, long epoch, OverviewQuery q) {
        try {
            String json=redis.opsForValue().get(key);
            if (json==null) return null;
            AssetWorkOrderOverview value=mapper.readValue(json,AssetWorkOrderOverview.class);
            if (value!=null && AssetWorkOrderOverview.METRIC_VERSION.equals(value.metricVersion())
                    && value.companyId()==scope.companyId() && value.assetId()==assetId && value.epoch()==epoch
                    && q.getStart().equals(value.start()) && q.getEnd().equals(value.end())
                    && q.isIncludeArchived()==value.includeArchived() && q.isIncludeDemo()==value.includeDemo()
                    && value.generatedAt()!=null) return value;
        } catch (org.springframework.dao.DataAccessException | JsonProcessingException ignored) { redisError("read-or-decode"); }
        return null;
    }

    public void release(String lockKey,String token) {
        try { redis.execute(unlock,List.of(lockKey),token); }
        catch (org.springframework.dao.DataAccessException ignored) { redisError("unlock"); }
    }
    private static void redisError(String operation) { Metrics.counter("cmms.query.cache.errors","operation",operation).increment(); }
    private static CustomException busy() { Metrics.counter("cmms.query.overload").increment(); return new CustomException("Query capacity exceeded; retry later",HttpStatus.SERVICE_UNAVAILABLE); }
}
