package com.grash.sla;

import lombok.RequiredArgsConstructor;
import org.quartz.*;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@DisallowConcurrentExecution
public class MaintenanceSlaJob extends QuartzJobBean {
    private final MaintenanceSlaService service;
    @Override protected void executeInternal(JobExecutionContext context) {service.processDue();}
}
