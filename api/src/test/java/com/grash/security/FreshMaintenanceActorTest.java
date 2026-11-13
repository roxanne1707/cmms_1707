package com.grash.security;

import com.grash.exception.CustomException;
import com.grash.model.User;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.RoleType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FreshMaintenanceActorTest {
    private final EntityManagerFactory factory=mock(EntityManagerFactory.class);
    private final EntityManager em=mock(EntityManager.class);
    private final User cached=mock(User.class,RETURNS_DEEP_STUBS);
    private final User fresh=mock(User.class,RETURNS_DEEP_STUBS);
    private final FreshMaintenanceActor loader=new FreshMaintenanceActor(factory);
    FreshMaintenanceActorTest() {
        when(factory.createEntityManager()).thenReturn(em);
        when(em.unwrap(Session.class)).thenReturn(mock(Session.class));
        when(cached.getId()).thenReturn(7L);
        when(cached.getCompany().getId()).thenReturn(1L);
        when(em.find(User.class,7L)).thenReturn(fresh);
        when(fresh.getId()).thenReturn(7L);
        when(fresh.getCompany().getId()).thenReturn(1L);
        when(fresh.isEnabled()).thenReturn(true);
        when(fresh.isEnabledInSubscription()).thenReturn(true);
        when(fresh.getRole().getRoleType()).thenReturn(RoleType.ROLE_CLIENT);
    }
    @Test void disabledDatabaseAccountOverridesCachedEnabledAccount() {
        when(cached.isEnabled()).thenReturn(true);when(fresh.isEnabled()).thenReturn(false);
        assertThrows(CustomException.class,()->loader.load(cached));verify(em).close();
    }
    @Test void movedCompanyRequiresNewAuthenticationInsteadOfOldAuditPrincipal() {
        when(fresh.getCompany().getId()).thenReturn(2L);
        assertThrows(CustomException.class,()->loader.load(cached));
    }
    @Test void oldRolePermissionsAreNeverReturned() {
        when(cached.getRole().getEditOtherPermissions()).thenReturn(java.util.Set.of(PermissionEntity.WORK_ORDERS));
        when(fresh.getRole().getEditOtherPermissions()).thenReturn(java.util.Set.of());
        User result=loader.load(cached);
        assertSame(fresh,result);assertTrue(result.getRole().getEditOtherPermissions().isEmpty());
    }
    @Test void relevantWritesAreCoveredAndReadOnlyHistogramIsNot() {
        assertTrue(FreshMaintenanceActor.applies("POST","/readings"));
        assertTrue(FreshMaintenanceActor.applies("PATCH","/part-quantities/work-order/7"));
        assertTrue(FreshMaintenanceActor.applies("POST","/parts/7/restock"));
        assertTrue(FreshMaintenanceActor.applies("PATCH","/purchase-orders/7/respond"));
        assertFalse(FreshMaintenanceActor.applies("GET","/readings/7"));
        assertFalse(FreshMaintenanceActor.applies("POST","/readings/meter/7/histogram"));
    }
}
