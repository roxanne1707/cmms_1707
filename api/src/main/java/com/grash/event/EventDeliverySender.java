package com.grash.event;

import com.grash.service.BrandingService;
import com.grash.utils.WebhookUrlValidator;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import jakarta.mail.internet.InternetAddress;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class EventDeliverySender {
    public record Outcome(String state,String reason) {
        static Outcome sent() { return new Outcome("SENT",null); }
        static Outcome skipped(String reason) { return new Outcome("SKIPPED",reason); }
    }
    private final ReliableEventProperties properties;
    private final EventAccessPolicy access;
    private final EventJson json;
    private final Environment environment;
    private final SpringTemplateEngine templates;
    private final BrandingService branding;
    private final JavaMailSender mailSender;
    private final ReportAttachmentStore attachments;

    public EventDeliverySender(ReliableEventProperties properties,EventAccessPolicy access,EventJson json,Environment environment,
                               @Qualifier("thymeleafTemplateEngine") SpringTemplateEngine templates,
                               BrandingService branding,JavaMailSender mailSender,ReportAttachmentStore attachments) {
        this.properties=properties; this.access=access; this.json=json; this.environment=environment;
        this.templates=templates; this.branding=branding; this.mailSender=mailSender; this.attachments=attachments;
    }

    public Outcome send(UUID deliveryId,String channel,DeliveryPayload payload) throws Exception {
        if(TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Network send must not run in a DB transaction");
        EventEnvelope event=payload.event();
        EventEnvelope.Target target=payload.target();
        Optional<EventAccessPolicy.WebhookDestination> webhook=Optional.empty();
        Optional<String> pushToken=Optional.empty();
        switch(channel) {
            case "EMAIL" -> {
                if(!access.canEmail(event,target)) return Outcome.skipped("RECIPIENT_REVOKED");
                if(!environment.getProperty("mail.enable",Boolean.class,false)) return Outcome.skipped("MAIL_DISABLED");
            }
            case "PUSH" -> {
                if(!access.canReceive(event,target)) return Outcome.skipped("RECIPIENT_REVOKED");
                pushToken=access.pushToken(event,target);
                if(pushToken.isEmpty()) return Outcome.skipped("NO_PUSH_TOKEN");
            }
            case "WEBHOOK" -> {
                webhook=access.webhook(event,payload.webhook());
                if(webhook.isEmpty()) return Outcome.skipped("ENDPOINT_OR_ENTITLEMENT_REVOKED");
            }
            default -> throw new IllegalArgumentException("Unsupported delivery channel");
        }
        ReportAttachmentStore.Attachment attachment=channel.equals("EMAIL") && target.purpose().equals("WORK_ORDER_REPORT")
                ? attachments.require(event.companyId(),event.eventId()) : null;
        if(!properties.getLocalHttpSink().isBlank()) {
            URI sink=URI.create(properties.getLocalHttpSink());
            if(!"http".equals(sink.getScheme()) || !Set.of("127.0.0.1","[::1]","::1").contains(sink.getHost()) || sink.getUserInfo()!=null)
                throw new IllegalArgumentException("Test sink must use a literal loopback HTTP address");
            if(channel.equals("WEBHOOK")) sendWebhook(deliveryId,payload,webhook.orElseThrow(),sink);
            else {
                Object body=attachment==null?payload:Map.of("delivery",payload,"attachmentName",attachment.filename(),"attachmentBase64",Base64.getEncoder().encodeToString(attachment.content()));
                post(sink,json.write(body),Map.of("Idempotency-Key",deliveryId.toString(),"X-Event-Id",event.eventId().toString()));
            }
            return Outcome.sent();
        }
        if(!properties.isExternalDeliveryEnabled()) return Outcome.skipped("EXTERNAL_DELIVERY_DISABLED");
        if(channel.equals("EMAIL")) {
            String type=environment.getProperty("mail.type","SMTP");
            if(type.equalsIgnoreCase("SENDGRID") && target.email().toLowerCase(Locale.ROOT).endsWith("@demo.com")) return Outcome.skipped("DEMO_ADDRESS");
            Context context=new Context(Locale.forLanguageTag(target.locale())); context.setVariables(target.variables());
            context.setVariable("environment",environment); context.setVariable("brandConfig",branding.getBrandConfig());
            context.setVariable("backgroundColor",branding.getMailBackgroundColor());
            String html=templates.process(target.template(),context);
            if(type.equalsIgnoreCase("SENDGRID")) {
                Mail mail=new Mail(new Email(environment.getRequiredProperty("sendgrid.from.email"),branding.getBrandConfig().getName()),
                        target.title(),new Email(target.email()),new Content("text/html",html));
                if(attachment!=null) {
                    var value=new com.sendgrid.helpers.mail.objects.Attachments();
                    value.setFilename(attachment.filename());value.setType("application/pdf");value.setDisposition("attachment");
                    value.setContent(Base64.getEncoder().encodeToString(attachment.content()));mail.addAttachments(value);
                }
                post(URI.create("https://api.sendgrid.com/v3/mail/send"),mail.build(),
                        Map.of("Authorization","Bearer "+environment.getRequiredProperty("sendgrid.api.key")));
            } else sendSmtp(target,html,attachment);
        } else if(channel.equals("PUSH")) {
            String reply=post(URI.create("https://exp.host/--/api/v2/push/send"),json.write(Map.of("to",pushToken.orElseThrow(),
                    "title",target.title(),"body",target.message(),"data",Map.of("type",target.notificationType(),"id",target.resourceId(),"eventId",event.eventId().toString()))),Map.of());
            com.fasterxml.jackson.databind.JsonNode result=json.read(reply,com.fasterxml.jackson.databind.JsonNode.class);
            if(!"ok".equals(result.path("data").path("status").asText())) throw new java.io.IOException("Push provider rejected delivery");
        } else {
            EventAccessPolicy.WebhookDestination destination=webhook.orElseThrow();
            WebhookUrlValidator.validate(destination.url());
            sendWebhook(deliveryId,payload,destination,URI.create(destination.url()));
        }
        // SENT means accepted by the SMTP/HTTP provider, not read by the human recipient.
        return Outcome.sent();
    }
    private void sendWebhook(UUID deliveryId,DeliveryPayload payload,EventAccessPolicy.WebhookDestination destination,URI uri) throws Exception {
        EventEnvelope event=payload.event();
        Map<String,Object> body=new LinkedHashMap<>(event.facts());
        Object serializedSnapshot=body.remove(event.eventType().equals("PART_QUANTITY_CHANGED")?"serializedPart":"serializedWorkOrder");
        body.put(payload.webhook().serializedField(),payload.webhook().serialize() && destination.serialize()?serializedSnapshot:null);
        body.put("occurredAt",event.occurredAt().toString()); body.put("companyId",event.companyId());
        body.put("eventId",event.eventId().toString()); body.put("schemaVersion",event.schemaVersion());
        String serialized=json.write(body);
        Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(destination.secret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        post(uri,serialized,Map.of("Idempotency-Key",deliveryId.toString(),"X-Webhook-Id",deliveryId.toString(),"X-Webhook-Event",payload.webhook().eventType(),
                "X-Webhook-Timestamp",Long.toString(System.currentTimeMillis()),"X-Webhook-Signature",HexFormat.of().formatHex(mac.doFinal(serialized.getBytes(StandardCharsets.UTF_8)))));
    }
    private String post(URI uri,String body,Map<String,String> headers) throws Exception {
        HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(properties.getNetworkTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpRequest.Builder request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(properties.getNetworkTimeoutSeconds()))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(request::header);
        HttpResponse<String> response=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()<200 || response.statusCode()>=300) throw new java.io.IOException("Delivery HTTP status "+response.statusCode());
        return response.body();
    }
    private void sendSmtp(EventEnvelope.Target target,String html,ReportAttachmentStore.Attachment attachment) throws Exception {
        if(!(mailSender instanceof JavaMailSenderImpl configured)) throw new IllegalStateException("Reliable SMTP requires configurable bounded JavaMail sender");
        JavaMailSenderImpl bounded=new JavaMailSenderImpl();
        bounded.setHost(configured.getHost()); bounded.setPort(configured.getPort()); bounded.setProtocol(configured.getProtocol());
        bounded.setUsername(configured.getUsername()); bounded.setPassword(configured.getPassword()); bounded.setDefaultEncoding("UTF-8");
        Properties options=new Properties(); options.putAll(configured.getJavaMailProperties());
        String timeout=Long.toString(properties.getNetworkTimeoutSeconds()*1000L);
        for(String protocol:List.of("smtp","smtps")) for(String property:List.of("connectiontimeout","timeout","writetimeout")) options.setProperty("mail."+protocol+"."+property,timeout);
        bounded.setJavaMailProperties(options);
        var message=bounded.createMimeMessage(); MimeMessageHelper helper=new MimeMessageHelper(message,true,"UTF-8");
        helper.setFrom(new InternetAddress(environment.getProperty("spring.mail.from",configured.getUsername()),branding.getBrandConfig().getName()));
        helper.setTo(target.email()); helper.setSubject(target.title()); helper.setText(html,true);
        if(attachment!=null) helper.addAttachment(attachment.filename(),new org.springframework.core.io.ByteArrayResource(attachment.content()),"application/pdf");
        bounded.send(message);
    }
}
