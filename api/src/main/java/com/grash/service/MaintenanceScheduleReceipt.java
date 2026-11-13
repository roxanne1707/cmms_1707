package com.grash.service;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
@Service
@RequiredArgsConstructor
public class MaintenanceScheduleReceipt {
    private final JdbcTemplate jdbc;
    private final com.grash.database.DatabasePlatform database;
    @Transactional(propagation=Propagation.MANDATORY)
    public boolean claim(long companyId,long scheduleId,String kind,Instant scheduledAt) {
        if(scheduledAt==null || !java.util.Set.of("CREATE","REMINDER").contains(kind)) throw new IllegalArgumentException("Invalid schedule occurrence");
        return jdbc.update(database.sql("INSERT INTO maintenance_schedule_execution(company_id,schedule_id,execution_kind,scheduled_at) VALUES(?,?,?,?) ON CONFLICT DO NOTHING","INSERT INTO maintenance_schedule_execution(company_id,schedule_id,execution_kind,scheduled_at) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE company_id=company_id"),
                companyId,scheduleId,kind,Timestamp.from(scheduledAt))==1;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void complete(long companyId,long scheduleId,String kind,Instant scheduledAt,Long workOrderId) {
        jdbc.update("UPDATE maintenance_schedule_execution SET work_order_id=? WHERE company_id=? AND schedule_id=? AND execution_kind=? AND scheduled_at=?",
                workOrderId,companyId,scheduleId,kind,Timestamp.from(scheduledAt));
    }
}
