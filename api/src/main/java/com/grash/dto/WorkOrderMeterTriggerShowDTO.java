package com.grash.dto;

import com.grash.model.enums.WorkOrderMeterTriggerCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Work order meter trigger display data transfer object")
public class WorkOrderMeterTriggerShowDTO extends WorkOrderBaseShowDTO {
    @Schema(description = "Indicates if the trigger is recurrent")
    private boolean recurrent;

    @Schema(description = "Explicit policy: PER_EPISODE opens again only after recovery; ONCE opens once for this trigger")
    private com.grash.model.enums.MeterEpisodePolicy episodePolicy;

    @Schema(description = "Name of the meter trigger")
    private String name;

    @Schema(description = "Condition that triggers the work order")
    private WorkOrderMeterTriggerCondition triggerCondition;

    @Schema(description = "Threshold value for the trigger condition")
    private int value;

    @Schema(description = "Wait time before triggering, in specified units")
    private int waitBefore;

}
