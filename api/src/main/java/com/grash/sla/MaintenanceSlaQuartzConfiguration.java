package com.grash.sla;

import org.quartz.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration
@ConditionalOnProperty(name="cmms.sla.enabled",havingValue="true")
public class MaintenanceSlaQuartzConfiguration {
    @Bean public JobDetail maintenanceSlaJobDetail() {
        return JobBuilder.newJob(MaintenanceSlaJob.class).withIdentity("maintenance-sla","maintenance").storeDurably().build();
    }
    @Bean public Trigger maintenanceSlaTrigger() {
        return TriggerBuilder.newTrigger().forJob(maintenanceSlaJobDetail()).withIdentity("maintenance-sla-trigger","maintenance")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(60).repeatForever()
                        .withMisfireHandlingInstructionNextWithRemainingCount()).build();
    }
}
