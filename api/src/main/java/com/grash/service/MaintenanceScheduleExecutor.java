package com.grash.service;

import com.grash.event.ReliableEventService;
import com.grash.factory.MailServiceFactory;
import com.grash.model.*;
import com.grash.model.enums.PermissionEntity;
import com.grash.repository.ScheduleRepository;
import com.grash.utils.Helper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

/** A proxy boundary independent of QuartzJobBean's final execute method. */
@Service
@RequiredArgsConstructor
public class MaintenanceScheduleExecutor {
    private final ScheduleRepository schedules;
    private final ScheduleService scheduleService;
    private final PreventiveMaintenanceService maintenance;
    private final MaintenanceScheduleReceipt receipts;
    private final ReliableEventService events;
    private final EntityManager em;
    private final UserService users;
    private final MailServiceFactory mail;
    private final MessageSource messageSource;
    @Value("${frontend.url}") private String frontendUrl;

    @Transactional
    public void execute(long scheduleId,Instant scheduledAt,boolean reminder) {
        if(scheduledAt==null) throw new IllegalArgumentException("A stable scheduled fire time is required");
        Schedule schedule=schedules.findById(scheduleId).orElse(null);
        if(schedule==null) return;
        em.lock(schedule,LockModeType.PESSIMISTIC_WRITE);em.refresh(schedule);
        if(schedule.isDisabled() || (schedule.getEndsOn()!=null && scheduledAt.isAfter(schedule.getEndsOn().toInstant()))) return;
        PreventiveMaintenance pm=schedule.getPreventiveMaintenance();
        // Reminder occurs before the work date; test recurrence against the intended work date.
        Instant workDate=scheduledAt;
        if(reminder) {
            int days=pm.getCompany().getCompanySettings().getGeneralPreferences().getDaysBeforePrevMaintNotification();
            var zone=java.time.ZoneId.of(pm.getCompany().getCompanySettings().getGeneralPreferences().getTimeZone());
            workDate=scheduledAt.atZone(zone).plusDays(days).toInstant();
        }
        if(!scheduleService.checkIfWeeklyShouldRun(schedule,Date.from(workDate))) return;
        String kind=reminder?"REMINDER":"CREATE";
        long companyId=pm.getCompany().getId();
        if(!receipts.claim(companyId,scheduleId,kind,scheduledAt)) return;
        if(reminder) {
            if(events.isEnabled()) events.preventiveReminder(pm,scheduleId+":"+scheduledAt);
            else legacyReminder(pm);
        } else {
            WorkOrder order=maintenance.createWorkOrderFromPreventiveMaintenance(pm);
            if(order==null) throw new IllegalStateException("Scheduled creation did not produce a work order");
            receipts.complete(companyId,scheduleId,kind,scheduledAt,order.getId());
        }
    }
    private void legacyReminder(PreventiveMaintenance pm) {
        Map<Long,User> unique=new LinkedHashMap<>();
        users.findWorkersByCompany(pm.getCompany().getId()).stream()
                .filter(u->u.getRole().getViewPermissions().contains(PermissionEntity.SETTINGS)).forEach(u->unique.put(u.getId(),u));
        pm.getUsers().forEach(u->unique.put(u.getId(),u));
        String[] recipients=unique.values().stream().filter(u->u.isEnabled() && u.getUserSettings().shouldEmailUpdatesForWorkOrders()).map(User::getEmail).toArray(String[]::new);
        if(recipients.length==0) return;
        Locale locale=Helper.getLocale(pm.getCompany());
        mail.getMailService().sendMessageUsingThymeleafTemplate(recipients,messageSource.getMessage("coming_wo",null,locale),
                Map.of("pmLink",frontendUrl+"/app/preventive-maintenances/"+pm.getId(),"pmTitle",pm.getTitle()),"coming-work-order.html",locale);
    }
}
