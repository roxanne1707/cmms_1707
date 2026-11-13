package com.grash.event;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableRabbit
@EnableScheduling
@ConditionalOnProperty(name="cmms.events.enabled",havingValue="true")
public class EventRabbitConfiguration {
    @Bean public Declarables eventTopology(ReliableEventProperties p) {
        TopicExchange exchange=new TopicExchange(p.getExchange(),true,false);
        DirectExchange dead=new DirectExchange(p.getDeadExchange(),true,false);
        Queue queue=QueueBuilder.durable(p.getQueue()).quorum().build();
        Queue deadQueue=QueueBuilder.durable(p.getDeadQueue()).quorum().build();
        return new Declarables(exchange,dead,queue,deadQueue,BindingBuilder.bind(queue).to(exchange).with("notification"),
                BindingBuilder.bind(deadQueue).to(dead).with("dead"));
    }
    @Bean public RabbitTemplate eventRabbitTemplate(ConnectionFactory connectionFactory,ReliableEventProperties p) {
        if(p.getLeaseSeconds()<=p.getConfirmTimeoutSeconds()+5 || p.getLeaseSeconds()<=p.getNetworkTimeoutSeconds()+5)
            throw new IllegalArgumentException("Event lease must exceed bounded network/confirm timeout by more than five seconds");
        if(p.getMaxAttempts()<1 || p.getBatchSize()<1 || p.getBatchSize()>100 || p.getNetworkTimeoutSeconds()<1 || p.getConfirmTimeoutSeconds()<1 || p.getDatabaseBackoffSeconds()<1) throw new IllegalArgumentException("Invalid event worker limits");
        if(!(connectionFactory instanceof CachingConnectionFactory factory)) throw new IllegalArgumentException("Events require correlated publisher confirms");
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        RabbitTemplate template=new RabbitTemplate(factory);
        template.setMandatory(true);
        template.setReturnsCallback(returned -> { /* CorrelationData owns the returned message before its confirm future completes. */ });
        return template;
    }
    @Bean public SimpleRabbitListenerContainerFactory eventListenerContainerFactory(ConnectionFactory connectionFactory,ReliableEventProperties properties) {
        SimpleRabbitListenerContainerFactory factory=new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setAutoStartup(properties.isListenerAutoStartup());
        factory.setConcurrentConsumers(1);
        factory.setPrefetchCount(4);
        factory.setDefaultRequeueRejected(true);
        return factory;
    }
    @Bean public ThreadPoolTaskScheduler eventTaskScheduler() {
        ThreadPoolTaskScheduler scheduler=new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3); scheduler.setThreadNamePrefix("reliable-event-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true); scheduler.setAwaitTerminationSeconds(15);
        return scheduler;
    }
}
