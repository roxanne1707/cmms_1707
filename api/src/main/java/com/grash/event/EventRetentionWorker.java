package com.grash.event;

import io.micrometer.core.instrument.Metrics;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${cmms.events.enabled:false} and ${cmms.events.workers-enabled:true} and ${cmms.events.retention.enabled:false}")
public class EventRetentionWorker {
    private final EventRetentionService retention;
    private final EventRetentionProperties properties;
    @Scheduled(scheduler="eventTaskScheduler",fixedDelayString="${cmms.events.retention.poll-ms:3600000}",initialDelayString="${cmms.events.retention.initial-delay-ms:3600000}")
    public void run() {
        try {retention.run(null,null,properties.isDryRun(),"Scheduled event retention");}
        catch(RuntimeException failure) {Metrics.counter("cmms.events.retention.runs","mode",properties.isDryRun()?"preview":"execute","outcome","failed").increment();}
    }
}
