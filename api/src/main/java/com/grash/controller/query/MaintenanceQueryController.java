package com.grash.controller.query;

import com.grash.dto.query.*;
import com.grash.service.query.AssetWorkOrderOverviewService;
import com.grash.service.query.MaintenanceLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/query")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_CLIENT')")
@ConditionalOnProperty(name="enhancement.query.enabled",havingValue="true")
public class MaintenanceQueryController {
    private final AssetWorkOrderOverviewService overviewService;
    private final MaintenanceLedgerService ledgerService;
    @PostMapping("/assets/{assetId}/work-order-overview")
    public AssetWorkOrderOverview overview(@PathVariable long assetId,
             @RequestParam(required=false) Long companyId,@RequestBody OverviewQuery query,Authentication authentication) {
        return overviewService.overview(authentication,companyId,assetId,query);
    }
    @PostMapping("/maintenance-ledger")
    public LedgerPage ledger(@RequestParam(required=false) Long companyId,
                             @RequestBody LedgerQuery query,Authentication authentication) {
        return ledgerService.search(authentication,companyId,query);
    }
}
