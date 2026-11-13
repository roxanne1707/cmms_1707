package com.grash.security;

import com.grash.dto.query.AuthorizedQueryScope;
import com.grash.exception.CustomException;
import com.grash.model.Asset;
import com.grash.model.User;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.RoleType;
import com.grash.utils.Helper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.CacheMode;
import org.hibernate.Session;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Each request gets a new persistence context, bypassing UserService and Hibernate caches.
 * Authentication supplies only an identity; cached role/subscription/entity values are never trusted.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "enhancement.query.enabled", havingValue = "true")
public class QueryAccessPolicy {
    private final EntityManagerFactory entityManagerFactory;

    public AuthorizedQueryScope authorize(Authentication authentication, Long requestedCompanyId,
                                           Long assetId, boolean ledger) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetail principal)
                || principal.getUser() == null || principal.getUser().getId() == null)
            throw new CustomException("Authentication required", HttpStatus.UNAUTHORIZED);
        try (EntityManager em = entityManagerFactory.createEntityManager()) {
            em.unwrap(Session.class).setCacheMode(CacheMode.IGNORE);
            User fresh = em.find(User.class, principal.getUser().getId());
            if (fresh == null || !fresh.isEnabled() || !fresh.isEnabledInSubscription()
                    || fresh.getRole().getRoleType() != RoleType.ROLE_CLIENT || !fresh.canSeeAnalytics()
                    || !fresh.getRole().getViewPermissions().contains(PermissionEntity.WORK_ORDERS)
                    || !fresh.getRole().getViewOtherPermissions().contains(PermissionEntity.WORK_ORDERS)
                    || !fresh.getRole().getViewPermissions().contains(PermissionEntity.ASSETS))
                denied();
            long companyId = Helper.resolveCompanyId(fresh, requestedCompanyId);
            // The ledger exposes all matching asset names, including when no asset filter is supplied.
            if (ledger && !fresh.getRole().getViewOtherPermissions().contains(PermissionEntity.ASSETS)) denied();
            if (assetId != null) {
                Asset asset = em.createQuery("select a from Asset a where a.id=:id and a.company.id=:company",
                                Asset.class).setParameter("id", assetId).setParameter("company", companyId)
                        .getResultStream().findFirst().orElse(null);
                if (asset == null || !asset.canBeViewedBy(fresh))
                    throw new CustomException("Asset not found", HttpStatus.NOT_FOUND);
            }
            return new AuthorizedQueryScope(fresh.getId(), companyId);
        }
    }

    private static void denied() { throw new CustomException("Access denied", HttpStatus.FORBIDDEN); }
}
