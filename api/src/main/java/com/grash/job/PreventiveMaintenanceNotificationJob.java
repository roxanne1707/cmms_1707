package com.grash.job;
import com.grash.service.MaintenanceScheduleExecutor;
import lombok.RequiredArgsConstructor;
import org.quartz.*;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
@DisallowConcurrentExecution
public class PreventiveMaintenanceNotificationJob implements Job {
    private final MaintenanceScheduleExecutor executor;
    @Override public void execute(JobExecutionContext context) throws JobExecutionException {
        try { executeInternal(context); }
        catch(RuntimeException failure) { MaintenanceJobRetry.schedule(context,true,failure); throw new JobExecutionException("Maintenance occurrence failed; persistent retry scheduled",failure,false); }
    }
    public void executeInternal(JobExecutionContext context) throws JobExecutionException {
        long id=context.getMergedJobDataMap().getLong("scheduleId");
        executor.execute(id,MaintenanceJobRetry.occurrence(context),true);
    }
}
