package com.grash.event;

import com.grash.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Private immutable PDF snapshot; only a delivery worker may load it, never a public URL. */
@Service
@RequiredArgsConstructor
public class ReportAttachmentStore {
    public static final int MAX_BYTES=10*1024*1024;
    private final JdbcTemplate jdbc;
    public record Attachment(String filename,byte[] content) {}
    @Transactional(propagation=Propagation.MANDATORY)
    public void save(long companyId,UUID eventId,String filename,byte[] pdf) {
        if(pdf==null || pdf.length==0 || pdf.length>MAX_BYTES)
            throw new CustomException("Report PDF must be between 1 byte and 10 MiB",HttpStatus.PAYLOAD_TOO_LARGE);
        if(filename==null || filename.isBlank() || filename.length()>180 || filename.contains("/") || filename.contains("\\") || filename.chars().anyMatch(Character::isISOControl))
            throw new CustomException("Invalid report attachment filename",HttpStatus.BAD_REQUEST);
        jdbc.update("INSERT INTO event_report_attachment(company_id,event_id,filename,content) VALUES(?,?,?,?)",companyId,eventId,filename,pdf);
    }
    @Transactional(readOnly=true)
    public Attachment require(long companyId,UUID eventId) {
        return jdbc.query("SELECT filename,content FROM event_report_attachment WHERE company_id=? AND event_id=?",
                (rs,n)->new Attachment(rs.getString(1),rs.getBytes(2)),companyId,eventId).stream().findFirst()
                .orElseThrow(()->new IllegalStateException("Report attachment unavailable"));
    }
    /** Explicit maintenance operation. Unsent/failed reports are retained for recovery. */
    @Transactional
    public int purgeCompletedOlderThan90Days() {
        return jdbc.update("""
            DELETE FROM event_report_attachment a WHERE a.created_at<CURRENT_TIMESTAMP-INTERVAL '90 days'
            AND EXISTS(SELECT 1 FROM consumer_receipt r WHERE r.company_id=a.company_id AND r.event_id=a.event_id)
            AND EXISTS(SELECT 1 FROM event_delivery d WHERE d.company_id=a.company_id AND d.event_id=a.event_id)
            AND NOT EXISTS(SELECT 1 FROM event_delivery d WHERE d.company_id=a.company_id AND d.event_id=a.event_id AND d.state NOT IN ('SENT','SKIPPED'))
            AND NOT EXISTS(SELECT 1 FROM event_retry r WHERE r.company_id=a.company_id AND r.event_id=a.event_id
                AND r.state IN ('READY','PROCESSING','DEAD') AND NOT EXISTS(
                    SELECT 1 FROM event_retry newer WHERE newer.company_id=r.company_id AND newer.event_id=r.event_id AND newer.handler=r.handler
                    AND (newer.replay_generation,newer.business_attempt)>(r.replay_generation,r.business_attempt)))
            """);
    }
}
