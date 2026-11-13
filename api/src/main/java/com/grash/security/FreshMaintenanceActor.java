package com.grash.security;

import com.grash.exception.CustomException;
import com.grash.model.User;
import com.grash.model.enums.RoleType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.CacheMode;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** HTTP command authorization must not reuse the five-minute cached User graph.
 * Only this explicitly initialized detached authorization graph leaves the short read scope;
 * it is never merged/saved as a user update. Domain entities use the command transaction's EM. */
@Component
@RequiredArgsConstructor
public class FreshMaintenanceActor {
    private final EntityManagerFactory entityManagerFactory;

    public User load(User identity) {
        if (identity == null || identity.getId() == null) denied();
        try (EntityManager em = entityManagerFactory.createEntityManager()) {
            em.unwrap(Session.class).setCacheMode(CacheMode.IGNORE);
            User user = em.find(User.class, identity.getId());
            if (user == null || !user.isEnabled() || !user.isEnabledInSubscription()
                    || user.getRole().getRoleType() != RoleType.ROLE_CLIENT) denied();
            // CompanyAudit still sees the authenticated principal. A moved account must log in again.
            if (identity.getCompany() == null || user.getCompany() == null
                    || !java.util.Objects.equals(identity.getCompany().getId(), user.getCompany().getId())) denied();
            // Every collection consulted by the command's existing canBeViewed/EditedBy rules.
            Hibernate.initialize(user.getRole());
            Hibernate.initialize(user.getRole().getCreatePermissions());
            Hibernate.initialize(user.getRole().getViewPermissions());
            Hibernate.initialize(user.getRole().getViewOtherPermissions());
            Hibernate.initialize(user.getRole().getEditOtherPermissions());
            Hibernate.initialize(user.getRole().getDeleteOtherPermissions());
            Hibernate.initialize(user.getCompany());
            Hibernate.initialize(user.getCompany().getSubscription());
            Hibernate.initialize(user.getCompany().getSubscription().getSubscriptionPlan());
            Hibernate.initialize(user.getCompany().getSubscription().getSubscriptionPlan().getFeatures());
            Hibernate.initialize(user.getCompany().getCompanySettings());
            Hibernate.initialize(user.getCompany().getCompanySettings().getGeneralPreferences());
            return user;
        }
    }

    public static boolean applies(String method, String path) {
        if ("GET".equals(method) || "HEAD".equals(method) || path == null) return false;
        return path.matches(".*/readings(?:/\\d+)?/?")
                || path.matches(".*/part-quantities(?:/.*)?")
                || path.matches(".*/purchase-orders/\\d+/respond/?")
                || path.matches(".*/parts/\\d+/restock/?");
    }

    private static void denied() { throw new CustomException("Access denied", HttpStatus.FORBIDDEN); }
}
