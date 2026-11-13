package com.grash.dto.query;

import com.grash.model.enums.Priority;
import com.grash.model.enums.Status;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class LedgerQuery extends OverviewQuery {
    private Long assetId;
    private Status status;
    private Priority priority;
    private Boolean meterTriggered;
    private int page = 0;
    private int size = 25;
    private Sort sort = Sort.CREATED_DESC;
    public enum Sort { CREATED_DESC, CREATED_ASC }

    @Override public void normalizeAndValidate() {
        super.normalizeAndValidate();
        if (assetId != null && assetId <= 0) bad("assetId must be positive");
        if (page < 0 || page > 10000 || size < 1 || size > 100 || sort == null)
            bad("page must be 0..10000 and size must be 1..100");
    }
    // Explicit legacy database codes; never depend on MyBatis' default enum handler.
    public Integer getStatusCode() {
        return status == null ? null : switch (status) {
            case OPEN -> 0; case IN_PROGRESS -> 1; case ON_HOLD -> 2; case COMPLETE -> 3;
        };
    }
    public Integer getPriorityCode() {
        return priority == null ? null : switch (priority) {
            case NONE -> 0; case LOW -> 1; case MEDIUM -> 2; case HIGH -> 3;
        };
    }
}
