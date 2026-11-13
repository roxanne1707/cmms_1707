package com.grash.dto.query;

import com.grash.model.enums.Priority;
import com.grash.model.enums.Status;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
public class MaintenanceLedgerRow {
    private long id;
    private String customId;
    private String title;
    private Long assetId;
    private String assetName;
    private Status status;
    private Priority priority;
    private Instant createdAt;
    private Instant completedOn;
    private boolean archived;
    private boolean demo;
    private BigDecimal cycleSeconds;
    private boolean meterTriggered;
}
