package com.grash.event;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name="cmms.events.enabled",havingValue="true")
public class EventRetentionMetrics implements MeterBinder {
    private final EventRetentionService retention;
    private final EventRetentionProperties properties;
    @Override public void bindTo(MeterRegistry registry) {
        Gauge.builder("cmms.events.retention.enabled",()->properties.isEnabled()?1:0).register(registry);
        Gauge.builder("cmms.events.retention.last.success.seconds",retention::lastSuccessEpochSeconds).register(registry);
        Gauge.builder("cmms.events.retention.overdue.seconds",retention::overdueSeconds).register(registry);
        Gauge.builder("cmms.events.retention.candidate.sample",retention::candidateSample).description("Bounded candidate sample, NOT total historical backlog").register(registry);
    }
}
