package com.grash.event;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grash.service.BrandingService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ReportDeliverySenderTest {
    private final ReliableEventProperties properties=new ReliableEventProperties();
    private final EventAccessPolicy access=mock(EventAccessPolicy.class);
    private final ReportAttachmentStore attachments=mock(ReportAttachmentStore.class);
    private final EventJson json=new EventJson(new ObjectMapper().findAndRegisterModules());
    private final EventEnvelope event=new EventEnvelope(1,UUID.randomUUID(),1,"WORK_ORDER_REPORT",9,10L,Instant.now(),List.of(),List.of(),Map.of());
    private final EventEnvelope.Target target=new EventEnvelope.Target(null,"customer@example.invalid","WORK_ORDER_REPORT","body","subject","WORK_ORDER",9,"work-order-report-email.html","en",Map.of("customerId",8),true,false);
    private EventDeliverySender sender() {
        StandardEnvironment environment=new StandardEnvironment();environment.getPropertySources().addFirst(new MapPropertySource("test",Map.of("mail.enable",true)));
        return new EventDeliverySender(properties,access,json,environment,mock(SpringTemplateEngine.class),mock(BrandingService.class),mock(JavaMailSender.class),attachments);
    }
    @Test void revokedRecipientCannotEvenLoadPrivateAttachment() throws Exception {
        assertEquals("RECIPIENT_REVOKED",sender().send(UUID.randomUUID(),"EMAIL",DeliveryPayload.forTarget(event,target)).reason());
        verifyNoInteractions(attachments);
    }
    @Test void localDeliveryContainsPersistedPdfAndStableIdWithoutExternalEmail() throws Exception {
        AtomicReference<String> body=new AtomicReference<>(),header=new AtomicReference<>();
        HttpServer sink=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        sink.createContext("/report",exchange->{body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));header.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));exchange.sendResponseHeaders(204,-1);exchange.close();});sink.start();
        try {
            properties.setExternalDeliveryEnabled(false);properties.setLocalHttpSink("http://127.0.0.1:"+sink.getAddress().getPort()+"/report");
            when(access.canEmail(event,target)).thenReturn(true);byte[] pdf="%PDF-immutable-snapshot".getBytes(StandardCharsets.UTF_8);
            when(attachments.require(1,event.eventId())).thenReturn(new ReportAttachmentStore.Attachment("report.pdf",pdf));
            UUID id=UUID.randomUUID();assertEquals("SENT",sender().send(id,"EMAIL",DeliveryPayload.forTarget(event,target)).state());
            assertEquals(id.toString(),header.get());assertTrue(body.get().contains(Base64.getEncoder().encodeToString(pdf)));
            assertEquals("SENT",sender().send(id,"EMAIL",DeliveryPayload.forTarget(event,target)).state());assertEquals(id.toString(),header.get());
        } finally {sink.stop(0);}
    }
}
