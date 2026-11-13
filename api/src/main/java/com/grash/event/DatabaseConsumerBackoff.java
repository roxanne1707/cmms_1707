package com.grash.event;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
@ConditionalOnProperty(name="cmms.events.enabled",havingValue="true")
public class DatabaseConsumerBackoff {
    private final RabbitListenerEndpointRegistry registry;
    private final JdbcTemplate jdbc;
    private final ReliableEventProperties properties;
    private final AtomicBoolean paused=new AtomicBoolean();
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"event-db-recovery");t.setDaemon(true);return t;});
    public DatabaseConsumerBackoff(RabbitListenerEndpointRegistry registry,JdbcTemplate jdbc,ReliableEventProperties properties) {
        this.registry=registry;this.jdbc=jdbc;this.properties=properties;
    }
    public void pause() {
        if(!paused.compareAndSet(false,true)) return;
        executor.execute(()->{
            var container=registry.getListenerContainer(WorkOrderEventListener.ID);
            if(container!=null) container.stop();
            executor.schedule(this::recover,properties.getDatabaseBackoffSeconds(),TimeUnit.SECONDS);
        });
    }
    private void recover() {
        try {
            jdbc.queryForObject("SELECT 1",Integer.class);
            var container=registry.getListenerContainer(WorkOrderEventListener.ID);
            if(container!=null) container.start();
            paused.set(false);
        } catch(RuntimeException e) { executor.schedule(this::recover,properties.getDatabaseBackoffSeconds(),TimeUnit.SECONDS); }
    }
    @PreDestroy public void close() { executor.shutdownNow(); }
}
