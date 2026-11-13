package com.grash.event;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "cmms.events")
public class ReliableEventProperties {
    private boolean enabled = false;
    private boolean listenerAutoStartup = true;
    private int batchSize = 20;
    private int maxAttempts = 5;
    private int leaseSeconds = 60;
    private int confirmTimeoutSeconds = 10;
    private int networkTimeoutSeconds = 10;
    private int databaseBackoffSeconds = 10;
    private String exchange = "cmms.work-order.events";
    private String queue = "cmms.notification.dispatch";
    private String deadExchange = "cmms.work-order.dead";
    private String deadQueue = "cmms.notification.dead";
    // Test-only redirect. Only literal loopback addresses are accepted by the sender.
    private String localHttpSink = "";
    private boolean externalDeliveryEnabled = true;
}
