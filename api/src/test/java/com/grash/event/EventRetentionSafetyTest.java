package com.grash.event;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EventRetentionSafetyTest {
    @Test void retentionIsDisabledAndPreviewOnlyByDefault() {
        var properties=new EventRetentionProperties();assertFalse(properties.isEnabled());assertTrue(properties.isDryRun());properties.validate();
    }
    @Test void unsafeBudgetsAreRejected() {
        var properties=new EventRetentionProperties();properties.setRetainDays(0);assertThrows(IllegalArgumentException.class,properties::validate);
        properties.setRetainDays(90);properties.setBatchSize(100000);assertThrows(IllegalArgumentException.class,properties::validate);
        properties.setBatchSize(20);properties.setTransactionTimeoutSeconds(60);assertThrows(IllegalArgumentException.class,properties::validate);
    }
    @Test void apiMustExplicitlySelectPreviewOrExecution() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();assertFalse(validator.validate(new EventRetentionController.RunRequest(null,"Preview first")).isEmpty());
            assertFalse(validator.validate(new EventRetentionController.RunRequest(false,"x")).isEmpty());
            assertTrue(validator.validate(new EventRetentionController.RunRequest(true,"Preview first")).isEmpty());
        }
    }
}
