package com.grash.job;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.*;
import java.time.Instant;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MaintenanceJobRetryTest {
    private final Instant original=Instant.parse("2026-11-14T08:00:00Z");
    private JobExecutionContext context(JobDataMap data,Scheduler scheduler) {
        JobExecutionContext context=mock(JobExecutionContext.class);when(context.getMergedJobDataMap()).thenReturn(data);when(context.getScheduler()).thenReturn(scheduler);
        when(context.getScheduledFireTime()).thenReturn(Date.from(original));return context;
    }
    @Test void retryIsDurableDelayedAndKeepsOriginalOccurrence() throws Exception {
        Scheduler scheduler=mock(Scheduler.class);JobDataMap data=new JobDataMap();data.put("scheduleId",9L);
        long before=System.currentTimeMillis();MaintenanceJobRetry.schedule(context(data,scheduler),false,new IllegalStateException("transient"));
        var job=ArgumentCaptor.forClass(JobDetail.class);var trigger=ArgumentCaptor.forClass(Trigger.class);verify(scheduler).scheduleJob(job.capture(),trigger.capture());
        assertEquals(original.toEpochMilli(),job.getValue().getJobDataMap().getLong(MaintenanceJobRetry.OCCURRENCE));assertEquals(1,job.getValue().getJobDataMap().getInt(MaintenanceJobRetry.ATTEMPT));
        assertTrue(job.getValue().requestsRecovery());assertTrue(trigger.getValue().getStartTime().getTime()>=before+60000);
    }
    @Test void retryDoesNotUseItsOwnLaterFireTime() throws Exception {
        JobDataMap data=new JobDataMap();data.put(MaintenanceJobRetry.OCCURRENCE,original.toEpochMilli());
        JobExecutionContext context=context(data,mock(Scheduler.class));when(context.getScheduledFireTime()).thenReturn(new Date());assertEquals(original,MaintenanceJobRetry.occurrence(context));
    }
    @Test void recoveryUsesOriginalQuartzTriggerTimestamp() throws Exception {
        JobDataMap data=new JobDataMap();data.put(Scheduler.FAILED_JOB_ORIGINAL_TRIGGER_SCHEDULED_FIRETIME_IN_MILLISECONDS,Long.toString(original.toEpochMilli()));
        JobExecutionContext context=context(data,mock(Scheduler.class));when(context.isRecovering()).thenReturn(true);when(context.getScheduledFireTime()).thenReturn(new Date());assertEquals(original,MaintenanceJobRetry.occurrence(context));
    }
    @Test void exhaustedRetriesDoNotCreateAnUnboundedLoop() {
        JobDataMap data=new JobDataMap();data.put(MaintenanceJobRetry.ATTEMPT,3);Scheduler scheduler=mock(Scheduler.class);
        assertThrows(JobExecutionException.class,()->MaintenanceJobRetry.schedule(context(data,scheduler),false,new IllegalStateException("permanent")));verifyNoInteractions(scheduler);
    }
    @Test void duplicateRetryRegistrationIsSafe() throws Exception {
        JobDataMap data=new JobDataMap();data.put("scheduleId",9L);Scheduler scheduler=mock(Scheduler.class);
        when(scheduler.scheduleJob(any(JobDetail.class),any(Trigger.class))).thenThrow(new ObjectAlreadyExistsException("duplicate"));
        assertDoesNotThrow(()->MaintenanceJobRetry.schedule(context(data,scheduler),true,new IllegalStateException("transient")));
    }
}
