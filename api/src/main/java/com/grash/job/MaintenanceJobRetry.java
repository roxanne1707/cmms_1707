package com.grash.job;
import org.quartz.*;
import java.time.Instant;
import java.util.Date;

/** Persistent bounded retry triggers. The original occurrence, not retry wall time, is the business key. */
final class MaintenanceJobRetry {
    static final String OCCURRENCE="maintenanceOccurrenceEpochMs",ATTEMPT="maintenanceRetryAttempt";
    private static final long[] DELAY_SECONDS={60,300,1800};
    private MaintenanceJobRetry() {}
    static Instant occurrence(JobExecutionContext context) throws JobExecutionException {
        JobDataMap data=context.getMergedJobDataMap();
        if(data.containsKey(OCCURRENCE)) return Instant.ofEpochMilli(data.getLong(OCCURRENCE));
        if(context.isRecovering() && data.containsKey(Scheduler.FAILED_JOB_ORIGINAL_TRIGGER_SCHEDULED_FIRETIME_IN_MILLISECONDS))
            return Instant.ofEpochMilli(Long.parseLong(String.valueOf(data.get(Scheduler.FAILED_JOB_ORIGINAL_TRIGGER_SCHEDULED_FIRETIME_IN_MILLISECONDS))));
        if(context.getScheduledFireTime()==null) throw new JobExecutionException("Missing stable scheduled fire time");
        return context.getScheduledFireTime().toInstant();
    }
    static void schedule(JobExecutionContext context,boolean reminder,RuntimeException failure) throws JobExecutionException {
        JobDataMap data=context.getMergedJobDataMap();int attempt=data.containsKey(ATTEMPT)?data.getInt(ATTEMPT):0;
        if(attempt>=DELAY_SECONDS.length) throw new JobExecutionException("Maintenance retries exhausted; inspect scheduler failure and replay original occurrence",failure,false);
        Instant occurrence=occurrence(context);long scheduleId=data.getLong("scheduleId");
        JobDataMap next=new JobDataMap();next.put("scheduleId",scheduleId);next.put(OCCURRENCE,occurrence.toEpochMilli());next.put(ATTEMPT,attempt+1);
        String group=reminder?"notif-retry":"wo-retry";
        String name="maintenance-retry-"+scheduleId+"-"+occurrence.toEpochMilli()+"-"+(attempt+1);
        JobDetail job=JobBuilder.newJob(reminder?PreventiveMaintenanceNotificationJob.class:WorkOrderCreationJob.class)
                .withIdentity(name,group).usingJobData(next).requestRecovery(true).build();
        Trigger trigger=TriggerBuilder.newTrigger().withIdentity(name,group).startAt(Date.from(Instant.now().plusSeconds(DELAY_SECONDS[attempt])))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withRepeatCount(0).withMisfireHandlingInstructionFireNow()).build();
        try {context.getScheduler().scheduleJob(job,trigger);}
        catch(ObjectAlreadyExistsException alreadyScheduled) { /* Another attempt owner already persisted this retry. */ }
        catch(SchedulerException schedulingFailure) {failure.addSuppressed(schedulingFailure);throw new JobExecutionException("Unable to persist maintenance retry",failure,false);}
    }
}
