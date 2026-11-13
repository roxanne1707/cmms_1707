package com.grash.configuration;
import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;
class EmailMessageLocaleTest {
    @Test void explicitRecipientLocalesResolveIndependentlyOfTheHostLanguage() {
        var messages=new EmailConfiguration().emailMessageSource();
        assertEquals("Work Order Report",messages.getMessage("workOrderReport",null,Locale.ENGLISH));
        assertEquals("工单报告",messages.getMessage("workOrderReport",null,Locale.SIMPLIFIED_CHINESE));
        assertEquals("Work Order Report",messages.getMessage("workOrderReport",null,Locale.forLanguageTag("zz-ZZ")));
        assertNotEquals(messages.getMessage("workOrderReport",null,Locale.ENGLISH),messages.getMessage("workOrderReport",null,Locale.FRANCE));
    }
}
