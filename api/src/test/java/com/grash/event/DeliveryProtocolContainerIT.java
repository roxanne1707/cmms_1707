package com.grash.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grash.dto.BrandConfig;
import com.grash.service.BrandingService;
import com.sun.net.httpserver.HttpServer;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Explicit container suite: no disabledWithoutDocker and no local-HTTP SMTP shortcut.
 * Mailpit captures SMTP locally; no relay, real inbox or push device is contacted.
 */
@Testcontainers
class DeliveryProtocolContainerIT {
    @Container
    static final GenericContainer<?> mailpit=new GenericContainer<>("axllent/mailpit:v1.33.1")
            .withExposedPorts(1025,8025)
            .withEnv("MP_SMTP_AUTH_ACCEPT_ANY","1")
            .withEnv("MP_SMTP_AUTH_ALLOW_INSECURE","1")
            .waitingFor(Wait.forHttp("/api/v1/info").forPort(8025));

    private final ReliableEventProperties properties=new ReliableEventProperties();
    private final EventAccessPolicy access=mock(EventAccessPolicy.class);
    private final BrandingService branding=mock(BrandingService.class);
    private final ReportAttachmentStore attachments=mock(ReportAttachmentStore.class);
    private final JavaMailSenderImpl mail=new JavaMailSenderImpl();
    private final StandardEnvironment environment=new StandardEnvironment();
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String subject="维修通知 "+UUID.randomUUID();

    @BeforeEach void configureRealSmtp() {
        properties.setExternalDeliveryEnabled(true);
        properties.setNetworkTimeoutSeconds(1);
        mail.setHost(mailpit.getHost());mail.setPort(mailpit.getMappedPort(1025));
        mail.setUsername("synthetic-smtp-user");mail.setPassword("synthetic-smtp-test-only");
        mail.getJavaMailProperties().setProperty("mail.smtp.auth","true");
        environment.getPropertySources().addFirst(new MapPropertySource("container-test",Map.of(
                "mail.enable",true,"mail.type","SMTP","spring.mail.from","maintenance@example.invalid")));
        when(access.canEmail(any(),any())).thenReturn(true);
        when(branding.getBrandConfig()).thenReturn(BrandConfig.builder().name("Atlas 设备维护").build());
        when(branding.getMailBackgroundColor()).thenReturn("#ffffff");
    }

    private EventDeliverySender sender() {
        SpringTemplateEngine engine=new SpringTemplateEngine();engine.setTemplateResolver(new StringTemplateResolver());
        return new EventDeliverySender(properties,access,new EventJson(mapper),environment,engine,branding,mail,attachments);
    }
    private DeliveryPayload payload(String purpose) {
        var target=new EventEnvelope.Target(1L,"recipient@example.invalid",purpose,"轴承温度异常",subject,
                "WORK_ORDER",42,"<p th:text='${message}'></p>","zh",Map.of("message","轴承温度异常，请检查"),true,false);
        var event=new EventEnvelope(1,UUID.randomUUID(),1,"WORK_ORDER_CREATED",42,null,Instant.now(),List.of(),List.of(),Map.of());
        return DeliveryPayload.forTarget(event,target);
    }
    private byte[] get(String path) throws Exception {
        var response=http.send(HttpRequest.newBuilder(URI.create("http://"+mailpit.getHost()+":"+mailpit.getMappedPort(8025)+path))
                .timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200,response.statusCode());return response.body();
    }
    private Optional<String> capturedId() throws Exception {
        for(var message:mapper.readTree(get("/api/v1/messages")).path("messages"))
            if(subject.equals(message.path("Subject").asText()))return Optional.of(message.path("ID").asText());
        return Optional.empty();
    }
    private MimeMessage captured() throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        Optional<String> id;
        do {id=capturedId();if(id.isPresent())break;Thread.sleep(50);} while(System.nanoTime()<deadline);
        assertTrue(id.isPresent(),"SMTP accepted message must be retrievable from the capture server");
        return new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(get("/api/v1/message/"+id.orElseThrow()+"/raw")));
    }
    private List<Part> leaves(Part part) throws Exception {
        if(part.getContent() instanceof Multipart multipart) {
            List<Part> result=new ArrayList<>();
            for(int i=0;i<multipart.getCount();i++)result.addAll(leaves(multipart.getBodyPart(i)));
            return result;
        }
        return List.of(part);
    }

    @Test void realSmtpAuthMimeAndChineseBodyReachCaptureServer() throws Exception {
        assertEquals("SENT",sender().send(UUID.randomUUID(),"EMAIL",payload("ASSIGNMENT")).state());
        MimeMessage received=captured();assertEquals(subject,received.getSubject());
        assertTrue(received.getFrom()[0].toString().contains("maintenance@example.invalid"));
        assertEquals("recipient@example.invalid",received.getAllRecipients()[0].toString());
        boolean found=false;
        for(Part part:leaves(received))if(part.isMimeType("text/html")) {
            assertTrue(part.getContent().toString().contains("轴承温度异常，请检查"));found=true;
        }
        assertTrue(found);
    }
    @Test void reportAttachmentReachesSmtpWithoutByteChanges() throws Exception {
        DeliveryPayload payload=payload("WORK_ORDER_REPORT");
        byte[] pdf="%PDF-1.4\nsynthetic maintenance report\n%%EOF".getBytes(StandardCharsets.UTF_8);
        when(attachments.require(1,payload.event().eventId())).thenReturn(new ReportAttachmentStore.Attachment("maintenance.pdf",pdf));
        assertEquals("SENT",sender().send(UUID.randomUUID(),"EMAIL",payload).state());
        boolean found=false;
        for(Part part:leaves(captured()))if("maintenance.pdf".equals(part.getFileName())) {
            assertTrue(part.isMimeType("application/pdf"));assertArrayEquals(pdf,part.getInputStream().readAllBytes());found=true;
        }
        assertTrue(found);
    }
    @Test void revokedRecipientProducesNoSmtpMessage() throws Exception {
        when(access.canEmail(any(),any())).thenReturn(false);
        assertEquals("RECIPIENT_REVOKED",sender().send(UUID.randomUUID(),"EMAIL",payload("ASSIGNMENT")).reason());
        assertTrue(capturedId().isEmpty());
    }
    @Test void disabledExternalDeliveryProducesNoSmtpMessage() throws Exception {
        properties.setExternalDeliveryEnabled(false);
        assertEquals("EXTERNAL_DELIVERY_DISABLED",sender().send(UUID.randomUUID(),"EMAIL",payload("ASSIGNMENT")).reason());
        assertTrue(capturedId().isEmpty());
    }
    @Test void silentSmtpServerTimesOutRatherThanReportingSent() throws Exception {
        try(ServerSocket silent=new ServerSocket(0,1,java.net.InetAddress.getLoopbackAddress())) {
            var executor=Executors.newSingleThreadExecutor();
            var accepted=executor.submit(()->{try(var socket=silent.accept()){Thread.sleep(2200);}return null;});
            mail.setHost(silent.getInetAddress().getHostAddress());mail.setPort(silent.getLocalPort());
            try {
                assertTimeout(Duration.ofSeconds(4),()->assertThrows(MailException.class,
                        ()->sender().send(UUID.randomUUID(),"EMAIL",payload("ASSIGNMENT"))));
                assertTrue(capturedId().isEmpty());
            } finally {accepted.cancel(true);executor.shutdownNow();}
        }
    }
    private DeliveryPayload webhook() {
        var event=payload("ASSIGNMENT").event();
        var hook=new EventEnvelope.WebhookTarget(5,"NEW_WORK_ORDER",true,"newWorkOrder");
        when(access.webhook(any(),any())).thenReturn(Optional.of(new EventAccessPolicy.WebhookDestination("https://unused.example.invalid","synthetic-signing-key",true)));
        return DeliveryPayload.forWebhook(event,hook);
    }
    @Test void webhookFailureStatusPropagatesToRetryWorker() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{e.sendResponseHeaders(503,-1);e.close();});server.start();
        try {
            properties.setLocalHttpSink("http://127.0.0.1:"+server.getAddress().getPort()+"/");
            assertThrows(java.io.IOException.class,()->sender().send(UUID.randomUUID(),"WEBHOOK",webhook()));
        } finally {server.stop(0);}
    }
    @Test void webhookRedirectIsRejectedWithoutContactingRedirectTarget() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger reached=new AtomicInteger();
        server.createContext("/redirect",e->{e.getResponseHeaders().set("Location","/target");e.sendResponseHeaders(307,-1);e.close();});
        server.createContext("/target",e->{reached.incrementAndGet();e.sendResponseHeaders(200,-1);e.close();});server.start();
        try {
            properties.setLocalHttpSink("http://127.0.0.1:"+server.getAddress().getPort()+"/redirect");
            assertThrows(java.io.IOException.class,()->sender().send(UUID.randomUUID(),"WEBHOOK",webhook()));
            assertEquals(0,reached.get());
        } finally {server.stop(0);}
    }
    @Test void webhookTimeoutIsBounded() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{try{Thread.sleep(2200);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}finally{e.close();}});server.start();
        try {
            properties.setLocalHttpSink("http://127.0.0.1:"+server.getAddress().getPort()+"/");
            assertTimeout(Duration.ofSeconds(4),()->assertThrows(java.net.http.HttpTimeoutException.class,
                    ()->sender().send(UUID.randomUUID(),"WEBHOOK",webhook())));
        } finally {server.stop(0);}
    }
}
