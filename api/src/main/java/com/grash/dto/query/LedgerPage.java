package com.grash.dto.query;

import java.util.List;

public record LedgerPage(List<MaintenanceLedgerRow> content, long total, int page, int size) {
    public LedgerPage { content = List.copyOf(content); }
}
