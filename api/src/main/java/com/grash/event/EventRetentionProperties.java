package com.grash.event;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix="cmms.events.retention")
public class EventRetentionProperties {
    private boolean enabled=false;
    private boolean dryRun=true;
    private int retainDays=90;
    private int batchSize=20;
    private int maxRowsPerEvent=2000;
    private long maxPayloadBytesPerEvent=2097152;
    private int budgetMillis=10000;
    private int transactionTimeoutSeconds=5;
    @PostConstruct public void validate() {
        if(retainDays<1||retainDays>3650||batchSize<1||batchSize>200||maxRowsPerEvent<1||maxRowsPerEvent>10000
                ||maxPayloadBytesPerEvent<1024||maxPayloadBytesPerEvent>8388608||budgetMillis<100||budgetMillis>60000
                ||transactionTimeoutSeconds<1||transactionTimeoutSeconds>10)
            throw new IllegalArgumentException("Event retention bounds are invalid");
    }
}
