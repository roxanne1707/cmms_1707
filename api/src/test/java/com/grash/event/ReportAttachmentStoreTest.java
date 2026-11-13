package com.grash.event;
import com.grash.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ReportAttachmentStoreTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final ReportAttachmentStore store=new ReportAttachmentStore(jdbc,new com.grash.database.DatabasePlatform("postgresql",jdbc));
    @Test void oversizedAndEmptyAttachmentsNeverReachTheDatabase() {
        assertThrows(CustomException.class,()->store.save(1,UUID.randomUUID(),"report.pdf",new byte[ReportAttachmentStore.MAX_BYTES+1]));
        assertThrows(CustomException.class,()->store.save(1,UUID.randomUUID(),"report.pdf",new byte[0]));
        verifyNoInteractions(jdbc);
    }
    @Test void unsafeAttachmentNamesAreRejected() {
        for(String name:new String[]{"../report.pdf","dir\\report.pdf","report\r\n.pdf",""})
            assertThrows(CustomException.class,()->store.save(1,UUID.randomUUID(),name,new byte[]{1}));
        verifyNoInteractions(jdbc);
    }
}
