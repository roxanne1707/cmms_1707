package com.grash.event;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name="cmms.events.enabled",havingValue="true")
public class WorkOrderEventListener {
    public static final String ID="reliable-work-order-listener";
    private final EventJson json;
    private final EventStore store;
    private final NotificationCommandService notifications;
    private final DatabaseConsumerBackoff backoff;
    @RabbitListener(id=ID,queues="${cmms.events.queue:cmms.notification.dispatch}",containerFactory="eventListenerContainerFactory")
    public void receive(Message message,Channel channel) throws IOException {
        try {
            DispatchMessage dispatch;
            Optional<EventStore.Attempt> claim;
            try {
                if(message.getBody().length>16384) throw new IllegalArgumentException("Dispatch too large");
                dispatch=json.read(new String(message.getBody(),StandardCharsets.UTF_8),DispatchMessage.class);
                dispatch.validate();
                claim=store.claimAttempt(dispatch);
            } catch(IllegalArgumentException poison) {
                store.quarantine(fingerprint(message.getBody()),"INVALID_DISPATCH");
                channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
                return;
            }
            if(claim.isPresent()) {
                try { notifications.consume(claim.get()); }
                catch(RuntimeException failure) { store.failAttempt(claim.get(),failure,failure instanceof IllegalArgumentException); }
            }
            // consume() returned after commit, or another durable attempt/retry/dead record owns the work.
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        } catch(RuntimeException unavailable) {
            backoff.pause();
            throw new ImmediateRequeueAmqpException("Durable event handoff unavailable; consumer paused",unavailable);
        }
    }
    private static String fingerprint(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
