package com.grash.security;

import com.grash.exception.CustomException;
import com.grash.model.*;
import com.grash.model.enums.*;
import jakarta.persistence.*;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryAccessPolicyTest {
    private User user(){
        User user=mock(User.class);Company company=new Company();company.setId(10L);
        Role role=Role.builder().roleType(RoleType.ROLE_CLIENT)
                .viewPermissions(new HashSet<>(Set.of(PermissionEntity.ANALYTICS,PermissionEntity.ASSETS,PermissionEntity.WORK_ORDERS)))
                .viewOtherPermissions(new HashSet<>(Set.of(PermissionEntity.ASSETS,PermissionEntity.WORK_ORDERS))).build();
        when(user.getId()).thenReturn(1L);when(user.isEnabled()).thenReturn(true);
        when(user.isEnabledInSubscription()).thenReturn(true);when(user.getRole()).thenReturn(role);
        when(user.canSeeAnalytics()).thenReturn(true);when(user.getCompany()).thenReturn(company);
        when(user.getSuperAccountRelations()).thenReturn(List.of());
        return user;
    }
    @Test void freshContextRejectsRevocationEvenWithTheSameCachedPrincipal(){
        EntityManagerFactory factory=mock(EntityManagerFactory.class);
        EntityManager first=mock(EntityManager.class),second=mock(EntityManager.class);
        when(factory.createEntityManager()).thenReturn(first,second);
        when(first.unwrap(Session.class)).thenReturn(mock(Session.class));when(second.unwrap(Session.class)).thenReturn(mock(Session.class));
        User cached=user(),fresh=user(),revoked=user();when(revoked.canSeeAnalytics()).thenReturn(false);
        when(first.find(User.class,1L)).thenReturn(fresh);when(second.find(User.class,1L)).thenReturn(revoked);
        var auth=new UsernamePasswordAuthenticationToken(CustomUserDetail.builder().user(cached).build(),null,List.of());
        var policy=new QueryAccessPolicy(factory);
        assertEquals(10L,policy.authorize(auth,null,null,true).companyId());
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(CustomException.class,()->policy.authorize(auth,null,null,true)).getHttpStatus());
        verify(factory,times(2)).createEntityManager();verify(first).close();verify(second).close();
        verify(cached,never()).canSeeAnalytics();
    }
    @Test void rejectsAnonymousDisabledAndForeignCompany(){
        EntityManagerFactory factory=mock(EntityManagerFactory.class);QueryAccessPolicy policy=new QueryAccessPolicy(factory);
        assertEquals(HttpStatus.UNAUTHORIZED,assertThrows(CustomException.class,()->policy.authorize(null,null,null,false)).getHttpStatus());
        EntityManager em=mock(EntityManager.class);when(factory.createEntityManager()).thenReturn(em);
        when(em.unwrap(Session.class)).thenReturn(mock(Session.class));
        User fresh=user();when(em.find(User.class,1L)).thenReturn(fresh);
        var auth=new UsernamePasswordAuthenticationToken(CustomUserDetail.builder().user(fresh).build(),null,List.of());
        assertThrows(CustomException.class,()->policy.authorize(auth,20L,null,true));
        when(fresh.isEnabled()).thenReturn(false);assertThrows(CustomException.class,()->policy.authorize(auth,null,null,true));
    }
    @Test void assetVisibilityIsCheckedBeforeReturningScope(){
        EntityManagerFactory factory=mock(EntityManagerFactory.class);EntityManager em=mock(EntityManager.class);
        when(factory.createEntityManager()).thenReturn(em);when(em.unwrap(Session.class)).thenReturn(mock(Session.class));
        User fresh=user();when(em.find(User.class,1L)).thenReturn(fresh);
        @SuppressWarnings("unchecked") TypedQuery<Asset> query=mock(TypedQuery.class);
        when(em.createQuery(anyString(),eq(Asset.class))).thenReturn(query);
        when(query.setParameter(anyString(),any())).thenReturn(query);
        Asset asset=mock(Asset.class);when(asset.canBeViewedBy(fresh)).thenReturn(false);
        when(query.getResultStream()).thenReturn(Stream.of(asset));
        var auth=new UsernamePasswordAuthenticationToken(CustomUserDetail.builder().user(fresh).build(),null,List.of());
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(CustomException.class,()->new QueryAccessPolicy(factory)
                .authorize(auth,null,100L,false)).getHttpStatus());
    }
}
