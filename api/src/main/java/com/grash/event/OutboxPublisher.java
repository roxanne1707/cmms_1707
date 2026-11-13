package com.grash.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@ConditionalOnExpression("${cmms.events.enabled:false} and ${cmms.events.workers-enabled:true}")
public class OutboxPublisher {
    private final EventStore store;
    private final RabbitTemplate template;
    private final ReliableEventProperties properties;
    public OutboxPublisher(EventStore store,@Qualifier("eventRabbitTemplate") RabbitTemplate template,ReliableEventProperties properties) {
        this.store=store; this.template=template; this.properties=properties;
    }
    @Scheduled(scheduler="eventTaskScheduler",fixedDelayString="${cmms.events.publish-poll-ms:500}")
    public void publishPending() {
        try {
            store.recoverExpiredAttempts();
            for(EventStore.Publication publication:store.claimPublications()) publish(publication);
        } catch(RuntimeException e) { log.warn("Outbox polling deferred: {}",RetryPolicy.error(e)); }
    }
    public void publish(EventStore.Publication publication) {
        CorrelationData correlation=new CorrelationData(publication.id()+":"+publication.token());
        MessageProperties props=new MessageProperties(); props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT); props.setMessageId(publication.id().toString());
        Message message=new Message(publication.dispatch().getBytes(StandardCharsets.UTF_8),props);
        try {
            boolean dead=publication.purpose().equals("DEAD");
            template.send(dead?properties.getDeadExchange():properties.getExchange(),dead?"dead":"notification",message,correlation);
            correlation.getFuture().orTimeout(properties.getConfirmTimeoutSeconds(),TimeUnit.SECONDS).whenCompleteAsync((confirm,error)->{
                boolean accepted=error==null && confirm.isAck() && correlation.getReturned()==null;
                String reason=error!=null?RetryPolicy.error(error):correlation.getReturned()!=null?"UNROUTABLE":accepted?null:"NACK";
                try { store.publicationResult(publication,accepted,reason); }
                catch(RuntimeException persistenceFailure) { log.warn("Publish outcome will recover through lease: {}",publication.id()); }
            });
        } catch(RuntimeException e) { store.publicationResult(publication,false,RetryPolicy.error(e)); }
    }
}
