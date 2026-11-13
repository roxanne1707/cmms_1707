package com.grash.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grash.service.BrandingService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventDeliverySenderTest {
    private final ReliableEventProperties properties=new ReliableEventProperties();
    private final EventAccessPolicy access=mock(EventAccessPolicy.class);
    private final EventJson json=new EventJson(new ObjectMapper().findAndRegisterModules());
    private EventDeliverySender sender() {
        StandardEnvironment env=new StandardEnvironment();env.getPropertySources().addFirst(new MapPropertySource("test",Map.of("mail.enable",true)));
        return new EventDeliverySender(properties,access,json,env,mock(SpringTemplateEngine.class),mock(BrandingService.class),mock(JavaMailSender.class),mock(ReportAttachmentStore.class));
    }
    @Test void localWebhookUsesRealSignatureStableIdAndLegacySerializationField() throws Exception {
        AtomicReference<String> body=new AtomicReference<>(),signature=new AtomicReference<>(),id=new AtomicReference<>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/hook",exchange->{body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            signature.set(exchange.getRequestHeaders().getFirst("X-Webhook-Signature"));id.set(exchange.getRequestHeaders().getFirst("X-Webhook-Id"));
            exchange.sendResponseHeaders(200,0);exchange.getResponseBody().close();});server.start();
        try {
            properties.setLocalHttpSink("http://127.0.0.1:"+server.getAddress().getPort()+"/hook");
            var event=new EventEnvelope(1,UUID.randomUUID(),1,"WORK_ORDER_CREATED",1,null,Instant.now(),List.of(),List.of(),
                    Map.of("workOrderId",1,"serializedWorkOrder",Map.of("id",1,"title","test")));
            var webhook=new EventEnvelope.WebhookTarget(5,"NEW_WORK_ORDER",true,"newWorkOrder");
            var payload=DeliveryPayload.forWebhook(event,webhook);UUID delivery=UUID.randomUUID();
            when(access.webhook(event,webhook)).thenReturn(Optional.of(new EventAccessPolicy.WebhookDestination("https://unused.example.invalid","test-secret",true)));
            assertEquals("SENT",sender().send(delivery,"WEBHOOK",payload).state());
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            assertEquals(HexFormat.of().formatHex(mac.doFinal(body.get().getBytes(StandardCharsets.UTF_8))),signature.get());
            assertEquals(delivery.toString(),id.get());assertTrue(body.get().contains("\"newWorkOrder\":{\""));assertFalse(body.get().contains("serializedWorkOrder"));
            when(access.webhook(event,webhook)).thenReturn(Optional.of(new EventAccessPolicy.WebhookDestination("https://unused.example.invalid","test-secret",false)));
            sender().send(delivery,"WEBHOOK",payload);
            assertTrue(body.get().contains("\"newWorkOrder\":null"));assertEquals(delivery.toString(),id.get());
        } finally {server.stop(0);}
    }
    @Test void testSinkCannotRedirectToAnExternalHost() {
        properties.setLocalHttpSink("http://example.invalid/collect");
        var target=new EventEnvelope.Target(1L,"test@example.invalid","ASSIGNMENT","test","test","WORK_ORDER",1,"unused","en",Map.of(),true,false);
        var event=new EventEnvelope(1,UUID.randomUUID(),1,"WORK_ORDER_CREATED",1,null,Instant.now(),List.of(),List.of(),Map.of());
        when(access.canEmail(any(),any())).thenReturn(true);
        assertThrows(IllegalArgumentException.class,()->sender().send(UUID.randomUUID(),"EMAIL",DeliveryPayload.forTarget(event,target)));
    }
}
