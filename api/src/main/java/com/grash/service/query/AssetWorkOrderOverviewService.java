package com.grash.service.query;

import com.grash.cache.AssetOverviewCache;
import com.grash.dto.query.*;
import com.grash.security.QueryAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name="enhancement.query.enabled",havingValue="true")
public class AssetWorkOrderOverviewService {
    private final QueryAccessPolicy access;
    private final AssetWorkOrderOverviewRepository repository;
    private final AssetOverviewCache cache;
    @org.springframework.beans.factory.annotation.Value("${enhancement.query.cache.enabled:true}")
    private boolean cacheEnabled = true;

    public AssetWorkOrderOverview overview(Authentication authentication,Long companyId,long assetId,OverviewQuery q) {
        q.normalizeAndValidate();
        AuthorizedQueryScope scope=access.authorize(authentication,companyId,assetId,false);
        long epoch=repository.epoch(scope.companyId(),assetId);
        if (!cacheEnabled) return repository.aggregate(scope,assetId,epoch,q);
        return cache.getOrLoad(scope,assetId,epoch,q,()->repository.aggregate(scope,assetId,epoch,q));
    }
}
