package com.grash.event;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataAccessResourceFailureException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkOrderEventListenerTest {
    private final EventJson json=new EventJson(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
    private final EventStore store=mock(EventStore.class);
    private final NotificationCommandService notifications=mock(NotificationCommandService.class);
    private final DatabaseConsumerBackoff backoff=mock(DatabaseConsumerBackoff.class);
    private final WorkOrderEventListener listener=new WorkOrderEventListener(json,store,notifications,backoff);
    private final Channel channel=mock(Channel.class);
    @Test void ackFollowsSynchronousCommitAndNotAsyncSubmission() throws Exception {
        EventEnvelope event=new EventEnvelope(1,UUID.randomUUID(),1,"WORK_ORDER_CREATED",1,null,Instant.now(),List.of(),List.of(),Map.of());
        DispatchMessage dispatch=DispatchMessage.original(event);
        EventStore.Attempt attempt=new EventStore.Attempt(UUID.randomUUID(),UUID.randomUUID(),dispatch,event);
        when(store.claimAttempt(dispatch)).thenReturn(Optional.of(attempt));
        listener.receive(message(json.write(dispatch)),channel);
        var ordered=inOrder(notifications,channel);
        ordered.verify(notifications).consume(attempt);
        ordered.verify(channel).basicAck(7,false);
    }
    @Test void failedDurableHandoffDoesNotAckAndPausesConsumer() throws Exception {
        EventEnvelope event=new EventEnvelope(1,UUID.randomUUID(),1,"WORK_ORDER_CREATED",1,null,Instant.now(),List.of(),List.of(),Map.of());
        DispatchMessage dispatch=DispatchMessage.original(event);
        EventStore.Attempt attempt=new EventStore.Attempt(UUID.randomUUID(),UUID.randomUUID(),dispatch,event);
        when(store.claimAttempt(dispatch)).thenReturn(Optional.of(attempt));
        doThrow(new IllegalStateException("business failure")).when(notifications).consume(attempt);
        doThrow(new DataAccessResourceFailureException("database down")).when(store).failAttempt(eq(attempt),any(),eq(false));
        assertThrows(ImmediateRequeueAmqpException.class,()->listener.receive(message(json.write(dispatch)),channel));
        verify(channel,never()).basicAck(anyLong(),anyBoolean()); verify(backoff).pause();
    }
    @Test void poisonMustBePersistedBeforeAck() throws Exception {
        listener.receive(message("invalid-json"),channel);
        var ordered=inOrder(store,channel); ordered.verify(store).quarantine(anyString(),eq("INVALID_DISPATCH"));
        ordered.verify(channel).basicAck(7,false);
        doThrow(new DataAccessResourceFailureException("offline")).when(store).quarantine(anyString(),anyString());
        assertThrows(ImmediateRequeueAmqpException.class,()->listener.receive(message("another-invalid-json"),channel));
        verify(channel,times(1)).basicAck(7,false);
    }
    private Message message(String value) {
        MessageProperties properties=new MessageProperties();properties.setDeliveryTag(7);
        return new Message(value.getBytes(StandardCharsets.UTF_8),properties);
    }
}
