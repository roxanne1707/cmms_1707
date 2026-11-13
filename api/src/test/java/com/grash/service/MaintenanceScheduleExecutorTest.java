package com.grash.service;
import com.grash.event.ReliableEventService;
import com.grash.factory.MailServiceFactory;
import com.grash.model.*;
import com.grash.repository.ScheduleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.context.MessageSource;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MaintenanceScheduleExecutorTest {
    private final ScheduleRepository repository=mock(ScheduleRepository.class);
    private final ScheduleService schedules=mock(ScheduleService.class);
    private final PreventiveMaintenanceService maintenance=mock(PreventiveMaintenanceService.class);
    private final MaintenanceScheduleReceipt receipts=mock(MaintenanceScheduleReceipt.class);
    private final ReliableEventService events=mock(ReliableEventService.class);
    private final EntityManager em=mock(EntityManager.class);
    private final MailServiceFactory mail=mock(MailServiceFactory.class);
    private final MaintenanceScheduleExecutor executor=new MaintenanceScheduleExecutor(repository,schedules,maintenance,receipts,events,em,mock(UserService.class),mail,mock(MessageSource.class));
    private final Instant occurrence=Instant.parse("2026-11-14T08:00:00Z");
    private Schedule schedule;
    @BeforeEach void fixture() {
        Company company=new Company();company.setId(1L);CompanySettings settings=new CompanySettings();
        GeneralPreferences preferences=new GeneralPreferences(settings);preferences.setTimeZone("Asia/Shanghai");preferences.setDaysBeforePrevMaintNotification(3);
        settings.setGeneralPreferences(preferences);company.setCompanySettings(settings);
        PreventiveMaintenance pm=new PreventiveMaintenance();pm.setId(2L);pm.setCompany(company);
        schedule=new Schedule(pm);schedule.setId(3L);schedule.setStartsOn(Date.from(occurrence.minusSeconds(86400)));
        when(repository.findById(3L)).thenReturn(Optional.of(schedule));
        when(schedules.checkIfWeeklyShouldRun(eq(schedule),any())).thenReturn(true);
    }
    @Test void duplicateOccurrenceDoesNotCreateOrNotify() {
        when(receipts.claim(1,3,"CREATE",occurrence)).thenReturn(false);
        executor.execute(3,occurrence,false);verifyNoInteractions(maintenance,events,mail);
    }
    @Test void reminderUsesIntendedWorkDateAndReliableOutboxOnly() {
        when(receipts.claim(1,3,"REMINDER",occurrence)).thenReturn(true);when(events.isEnabled()).thenReturn(true);
        executor.execute(3,occurrence,true);
        verify(schedules).checkIfWeeklyShouldRun(schedule,Date.from(occurrence.plusSeconds(3*86400)));
        verify(events).preventiveReminder(schedule.getPreventiveMaintenance(),"3:"+occurrence);verifyNoInteractions(mail);
    }
    @Test void failedCreationPropagatesAndDoesNotCompleteReceipt() {
        when(receipts.claim(1,3,"CREATE",occurrence)).thenReturn(true);
        when(maintenance.createWorkOrderFromPreventiveMaintenance(any())).thenThrow(new IllegalStateException("rollback"));
        assertThrows(IllegalStateException.class,()->executor.execute(3,occurrence,false));
        verify(receipts,never()).complete(anyLong(),anyLong(),anyString(),any(),any());
    }
    @Test void successfulCreationRecordsItsWorkOrder() {
        when(receipts.claim(1,3,"CREATE",occurrence)).thenReturn(true);WorkOrder order=new WorkOrder();order.setId(4L);
        when(maintenance.createWorkOrderFromPreventiveMaintenance(any())).thenReturn(order);
        executor.execute(3,occurrence,false);verify(receipts).complete(1,3,"CREATE",occurrence,4L);
    }
    @Test void missingOccurrenceCannotFallBackToWallClockAndDuplicateWork() {
        assertThrows(IllegalArgumentException.class,()->executor.execute(3,null,false));verifyNoInteractions(receipts);
    }
}
