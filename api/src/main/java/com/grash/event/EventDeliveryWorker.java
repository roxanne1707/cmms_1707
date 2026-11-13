package com.grash.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnExpression("${cmms.events.enabled:false} and ${cmms.events.workers-enabled:true}")
public class EventDeliveryWorker {
    private final EventDeliveryStore store;
    private final EventDeliverySender sender;
    private final EventJson json;
    @Scheduled(scheduler="eventTaskScheduler",fixedDelayString="${cmms.events.delivery-poll-ms:500}")
    public void deliverOne() {
        try {
            var optional=store.claim(); if(optional.isEmpty()) return;
            EventDeliveryStore.Delivery delivery=optional.get();
            try {
                EventDeliverySender.Outcome result=sender.send(delivery.id(),delivery.channel(),json.read(delivery.payload(),DeliveryPayload.class));
                store.finish(delivery,result.state(),result.reason());
            } catch(Exception failure) {
                if(failure instanceof InterruptedException) Thread.currentThread().interrupt();
                store.finish(delivery,failure instanceof IllegalArgumentException?"DEAD":store.failureState(delivery),RetryPolicy.error(failure));
            }
        } catch(RuntimeException unavailable) { log.warn("Delivery polling deferred: {}",RetryPolicy.error(unavailable)); }
    }
}
