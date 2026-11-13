package com.grash.service;

import com.grash.event.ReliableEventService;
import com.grash.model.*;
import com.grash.dto.workOrder.WorkOrderPostDTO;
import com.grash.model.enums.*;
import com.grash.model.enums.webhook.WebhookEvent;
import com.grash.mapper.WorkOrderMapper;
import com.grash.repository.WorkOrderMeterTriggerRepository;
import com.grash.utils.Helper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class MeterTriggerCommandService {
    private final WorkOrderMeterTriggerRepository triggers;
    private final WorkOrderService workOrders;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final ReliableEventService events;
    private final NotificationService notifications;
    private final WebhookDispatchService webhooks;
    private final WorkOrderMapper mapper;
    private final MessageSource messageSource;
    private record State(boolean abnormal, boolean opened, long episode, long openedCount, Instant firstAbnormal, String ruleHash) {}

    /** Caller holds the meter row lock; every new reading advances state in database acceptance order. */
    public void process(Meter meter, Reading reading, User actor) {
        List<WorkOrderMeterTrigger> rules = new ArrayList<>(triggers.findByMeter_Id(meter.getId()));
        rules.sort(Comparator.comparing(WorkOrderMeterTrigger::getId));
        for (WorkOrderMeterTrigger rule : rules) {
            em.refresh(rule, LockModeType.PESSIMISTIC_WRITE);
            String ruleHash = DomainCommandStore.hash(rule.getTriggerCondition() + ":" + rule.getValue() + ":" + rule.getWaitBefore() + ":" + rule.getEpisodePolicy());
            jdbc.update("INSERT INTO meter_trigger_state(trigger_id,company_id) VALUES (?,?) ON CONFLICT (trigger_id) DO NOTHING", rule.getId(), meter.getCompany().getId());
            State state = jdbc.queryForObject("SELECT abnormal,opened,episode,opened_count,first_abnormal_at,rule_hash FROM meter_trigger_state WHERE trigger_id=? FOR UPDATE",
                    (rs,n) -> new State(rs.getBoolean(1),rs.getBoolean(2),rs.getLong(3),rs.getLong(4),rs.getTimestamp(5)==null?null:rs.getTimestamp(5).toInstant(),rs.getString(6)),rule.getId());
            boolean abnormal = MeterEpisodeRules.abnormal(reading.getValue(), rule.getValue(), rule.getTriggerCondition());
            Instant now = Instant.now();
            // A changed rule starts a new evaluation episode; execution preserves the exact old rule fingerprint.
            boolean continuation = state.abnormal() && Objects.equals(state.ruleHash(), ruleHash);
            long episode = state.episode() + (abnormal && !continuation ? 1 : 0);
            boolean opened = abnormal && continuation && state.opened();
            Instant first = abnormal ? (continuation ? state.firstAbnormal() : now) : null;
            long count = state.openedCount();
            if (MeterEpisodeRules.shouldOpen(abnormal, opened, count, rule.getEpisodePolicy(), first, now, rule.getWaitBefore())) {
                WorkOrderPostDTO command = workOrders.getWorkOrderFromWorkOrderBase(rule);
                WorkOrder created = workOrders.create(command, meter.getCompany());
                jdbc.update("INSERT INTO meter_trigger_execution(company_id,trigger_id,episode,reading_id,work_order_id,rule_hash) VALUES (?,?,?,?,?,?)",
                        meter.getCompany().getId(),rule.getId(),episode,reading.getId(),created.getId(),ruleHash);
                String episodeKey = rule.getId() + ":" + episode;
                if (events.isEnabled()) events.meterEpisodeOpened(meter, rule, reading.getValue(), created, actor.getId(), episodeKey);
                else notifyAfterCommit(meter, rule, reading.getValue(), created, actor);
                opened = true;
                count++;
            }
            jdbc.update("UPDATE meter_trigger_state SET abnormal=?,opened=?,episode=?,opened_count=?,first_abnormal_at=?,last_reading_id=?,rule_hash=? WHERE trigger_id=?",
                    abnormal,opened,episode,count,first==null?null:Timestamp.from(first),reading.getId(),ruleHash,rule.getId());
        }
    }
    private void notifyAfterCommit(Meter meter, WorkOrderMeterTrigger rule, double value, WorkOrder wo, User actor) {
        Locale locale = Helper.getLocale(actor);
        String message = messageSource.getMessage(rule.getTriggerCondition()==WorkOrderMeterTriggerCondition.LESS_THAN ? "notification_reading_less_than" : "notification_reading_more_than",
                new Object[]{meter.getName(), rule.getValue(), meter.getUnit()},locale);
        String title = messageSource.getMessage("new_wo",null,locale);
        List<Notification> records = meter.getUsers().stream().map(u -> new Notification(message,u,NotificationType.METER,meter.getId())).toList();
        Map<String,Object> payload = new HashMap<>();
        payload.put("meterId",meter.getId()); payload.put("meterName",meter.getName()); payload.put("meterTriggerId",rule.getId());
        payload.put("meterTriggerName",rule.getName()); payload.put("readingValue",value); payload.put("triggerValue",rule.getValue());
        payload.put("triggerCondition",rule.getTriggerCondition().name()); payload.put("workOrderId",wo.getId());
        Object workOrderSnapshot = mapper.toShowDto(wo);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                // Compatibility fallback is best effort. Reliable delivery requires the event module enabled.
                try {
                    notifications.createMultiple(records,true,title);
                    webhooks.dispatchWebhook(meter.getCompany(),WebhookEvent.METER_TRIGGER_STATUS_CHANGE,payload,"triggeredWorkOrder",workOrderSnapshot,null,null,null,null,null);
                } catch (RuntimeException failure) { io.sentry.Sentry.captureException(failure); }
            }
        });
    }
}
