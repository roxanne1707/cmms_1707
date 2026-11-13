package com.grash.job;

import com.grash.exception.CustomException;
import com.grash.model.PreventiveMaintenance;
import com.grash.model.Schedule;
import com.grash.model.WorkOrder;
import com.grash.repository.ScheduleRepository;
import com.grash.service.PreventiveMaintenanceService;
import com.grash.service.ScheduleService;
import com.grash.service.MaintenanceScheduleExecutor;
import com.grash.service.MaintenanceScheduleReceipt;
import com.grash.event.ReliableEventService;
import jakarta.persistence.EntityManager;
import static org.mockito.Mockito.lenient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkOrderCreationJobTest {

    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private ScheduleService scheduleService;
    @Mock
    private PreventiveMaintenanceService preventiveMaintenanceService;

    @Mock private MaintenanceScheduleReceipt receipts;
    @Mock private ReliableEventService events;
    @Mock private EntityManager em;
    @InjectMocks private MaintenanceScheduleExecutor executor;
    private WorkOrderCreationJob workOrderCreationJob;

    private Schedule schedule;

    @BeforeEach
    void setUp() {
        workOrderCreationJob=new WorkOrderCreationJob(executor);
        lenient().when(receipts.claim(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any())).thenReturn(true);
        PreventiveMaintenance preventiveMaintenance = new PreventiveMaintenance();
        preventiveMaintenance.setId(1L);
        com.grash.model.Company company=new com.grash.model.Company();company.setId(1L);preventiveMaintenance.setCompany(company);
        schedule = new Schedule(preventiveMaintenance);
        schedule.setId(1L);
        schedule.setDisabled(false);
    }

    private JobExecutionContext contextWithScheduleId(long scheduleId) {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("scheduleId", scheduleId);
        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getMergedJobDataMap()).thenReturn(jobDataMap);
        when(context.getScheduledFireTime()).thenReturn(java.util.Date.from(java.time.Instant.parse("2026-11-14T08:00:00Z")));
        return context;
    }

    @Test
    void scheduleNotFound_returnsWithoutCreatingWorkOrder() {
        when(scheduleRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> workOrderCreationJob.executeInternal(contextWithScheduleId(1L)));

        verify(scheduleService, never()).checkIfWeeklyShouldRun(any(),any());
        verify(preventiveMaintenanceService, never()).createWorkOrderFromPreventiveMaintenance(any());
    }

    @Test
    void disabledSchedule_returnsWithoutCreatingWorkOrder() {
        schedule.setDisabled(true);
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));

        assertDoesNotThrow(() -> workOrderCreationJob.executeInternal(contextWithScheduleId(1L)));

        verify(scheduleService, never()).checkIfWeeklyShouldRun(any(),any());
        verify(preventiveMaintenanceService, never()).createWorkOrderFromPreventiveMaintenance(any());
    }

    @Test
    void weeklyShouldNotRun_returnsWithoutCreatingWorkOrder() {
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));
        when(scheduleService.checkIfWeeklyShouldRun(eq(schedule),org.mockito.ArgumentMatchers.any(java.util.Date.class))).thenReturn(false);

        assertDoesNotThrow(() -> workOrderCreationJob.executeInternal(contextWithScheduleId(1L)));

        verify(preventiveMaintenanceService, never()).createWorkOrderFromPreventiveMaintenance(any());
    }

    @Test
    void validSchedule_createsWorkOrderFromPreventiveMaintenance() {
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));
        when(scheduleService.checkIfWeeklyShouldRun(eq(schedule),org.mockito.ArgumentMatchers.any(java.util.Date.class))).thenReturn(true);
        WorkOrder workOrder = new WorkOrder();
        workOrder.setCustomId("WO000042");
        when(preventiveMaintenanceService.createWorkOrderFromPreventiveMaintenance(any()))
                .thenReturn(workOrder);

        assertDoesNotThrow(() -> workOrderCreationJob.executeInternal(contextWithScheduleId(1L)));

        verify(preventiveMaintenanceService).createWorkOrderFromPreventiveMaintenance(schedule.getPreventiveMaintenance());
    }

    @Test
    void workOrderCreationFails_rethrowsSoTheTransactionIsRolledBack() {
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));
        when(scheduleService.checkIfWeeklyShouldRun(eq(schedule),org.mockito.ArgumentMatchers.any(java.util.Date.class))).thenReturn(true);
        when(preventiveMaintenanceService.createWorkOrderFromPreventiveMaintenance(any()))
                .thenThrow(new CustomException("You need a license to add a new work order.", HttpStatus.FORBIDDEN));

        assertThrows(CustomException.class, () -> workOrderCreationJob.executeInternal(contextWithScheduleId(1L)));

        verify(preventiveMaintenanceService).createWorkOrderFromPreventiveMaintenance(schedule.getPreventiveMaintenance());
    }

    @Test
    void contextWithoutScheduleId_propagatesError() {
        JobDataMap jobDataMap = new JobDataMap();
        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getMergedJobDataMap()).thenReturn(jobDataMap);

        assertThrows(ClassCastException.class, () -> workOrderCreationJob.executeInternal(context));

        verify(scheduleRepository, never()).findById(anyLong());
        verify(preventiveMaintenanceService, never()).createWorkOrderFromPreventiveMaintenance(any());
    }
}
