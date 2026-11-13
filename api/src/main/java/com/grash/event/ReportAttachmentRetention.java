package com.grash.event;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class ReportAttachmentRetention {
    private final ReliableEventProperties properties;
    private final ReportAttachmentStore attachments;
    @Scheduled(fixedDelayString="${cmms.events.report-cleanup-delay-ms:86400000}",initialDelayString="${cmms.events.report-cleanup-delay-ms:86400000}")
    public void purgeCompletedReports() { if(properties.isEnabled()) attachments.purgeCompletedOlderThan90Days(); }
}
