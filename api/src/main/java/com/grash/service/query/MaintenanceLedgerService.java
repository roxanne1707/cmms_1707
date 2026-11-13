package com.grash.service.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.grash.dto.query.*;
import com.grash.readmodel.mapper.MaintenanceLedgerMapper;
import com.grash.security.QueryAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name="enhancement.query.enabled",havingValue="true")
public class MaintenanceLedgerService {
    private final QueryAccessPolicy access;
    private final MaintenanceLedgerMapper mapper;
    @org.springframework.transaction.annotation.Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public LedgerPage search(Authentication authentication,Long companyId,LedgerQuery q) {
        q.normalizeAndValidate();
        AuthorizedQueryScope scope=access.authorize(authentication,companyId,q.getAssetId(),true);
        // Explicit count avoids pagination SQL rewrites on future one-to-many joins.
        long total=mapper.count(scope.companyId(),q);
        Page<MaintenanceLedgerRow> page=new Page<>(q.getPage()+1L,q.getSize(),false);
        return new LedgerPage(mapper.selectPage(page,scope.companyId(),q),total,q.getPage(),q.getSize());
    }
}
