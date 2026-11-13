package com.grash.controller.analytics;

import com.grash.dto.DateRange;
import com.grash.exception.CustomException;
import com.grash.model.*;
import com.grash.model.enums.*;
import com.grash.service.*;
import com.grash.security.*;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsCorrectnessTest {
    private WorkOrder order(long created,long complete) {
        WorkOrder o=new WorkOrder();o.setCreatedAt(new Date(created));o.setCompletedOn(new Date(complete));o.setStatus(Status.COMPLETE);return o;
    }
    @Test void missingOrNegativeCycleIsExcludedAndAverageIsRoundedOnlyOnce() {
        WorkOrder a=order(0,23*3600000L),b=order(0,25*3600000L),negative=order(10,1),missing=order(0,1);missing.setCompletedOn(null);
        assertEquals(1,WorkOrder.getAverageAge(List.of(a,b,negative,missing)));
    }
    @Test void reportCycleUsesOriginalRequestStartWhenPresent() {
        WorkOrder o=order(86400000L,172800000L);Request r=new Request();r.setCreatedAt(new Date(0));o.setParentRequest(r);
        assertEquals(2,WorkOrder.getAverageAge(List.of(o)));
    }
    @Test void complianceHandlesMissingCompletionAndInclusiveDeadline() {
        WorkOrder o=order(0,1000);o.setDueDate(new Date(1000));assertTrue(o.isCompliant());
        o.setCompletedOn(null);assertFalse(o.isCompliant());o.setCompletedOn(new Date(-1));assertFalse(o.isCompliant());
    }
    @Test void nonOverlappingDowntimeDoesNotSubtractAvailability() {
        AssetDowntime downtime=new AssetDowntime();downtime.setStartsOn(new Date(0));downtime.setDuration(10);
        DateRange range=new DateRange();range.setStart(new Date(20000));range.setEnd(new Date(30000));
        assertEquals(0,downtime.getDateRangeDuration(range));
    }
    @Test void mobileComplianceDenominatorIsCompletedOrders() {
        WorkOrderService service=mock(WorkOrderService.class);User user=mock(User.class,RETURNS_DEEP_STUBS);
        when(user.getCompany().getId()).thenReturn(1L);
        WorkOrder completed=order(System.currentTimeMillis()-86400000,System.currentTimeMillis()-1000);
        WorkOrder open=order(0,100);open.setStatus(Status.OPEN);open.setCompletedOn(null);
        when(service.findByCompany(1L)).thenReturn(List.of(completed,open));
        var controller=new WOAnalyticsController(service,null,null,null,null,null,null);
        assertEquals(1.0,controller.getMobileExtendedStats(user).getBody().getCompliantRate());
    }
    @Test void crossCompanyAssetOverviewIsDeniedBeforeMetricsRun() {
        AssetService service=mock(AssetService.class);User user=new User();Company mine=new Company();mine.setId(1L);user.setCompany(mine);
        Asset asset=new Asset();Company other=new Company();other.setId(2L);asset.setCompany(other);
        when(service.findById(99L)).thenReturn(Optional.of(asset));
        var controller=new AssetAnalyticsController(null,null,service,null);
        assertThrows(CustomException.class,()->controller.getDateRangeOverview(99L,new DateRange(),user));
        verify(service,never()).getMTTR(anyLong(),any(),any());
    }
    @Test void analyticsRoleRevocationIsCheckedOnEveryCall() throws Throwable {
        var loader=mock(FreshMaintenanceActor.class);var guard=new AnalyticsAccessGuard(loader);
        var point=mock(ProceedingJoinPoint.class);var signature=mock(Signature.class);
        User identity=mock(User.class),fresh=mock(User.class);Role role=new Role();role.setViewPermissions(new HashSet<>(Set.of(PermissionEntity.WORK_ORDERS)));role.setViewOtherPermissions(new HashSet<>(Set.of(PermissionEntity.WORK_ORDERS)));
        when(fresh.getRole()).thenReturn(role);when(fresh.canSeeAnalytics()).thenReturn(true);when(loader.load(identity)).thenReturn(fresh);
        when(point.getArgs()).thenReturn(new Object[]{identity});when(point.getTarget()).thenReturn(new WOAnalyticsController(null,null,null,null,null,null,null));when(point.getSignature()).thenReturn(signature);when(signature.getName()).thenReturn("getCompleteStats");
        guard.authorize(point);role.getViewOtherPermissions().clear();
        assertThrows(CustomException.class,()->guard.authorize(point));verify(point,times(1)).proceed(any(Object[].class));verify(loader,times(2)).load(identity);
    }
    @Test void invalidAnalyticsRangeFailsBeforeQuery() {
        DateRange range=new DateRange();assertThrows(CustomException.class,()->AnalyticsAccessGuard.validateRange(range));
        range.setStart(new Date(2));range.setEnd(new Date(1));assertThrows(CustomException.class,()->AnalyticsAccessGuard.validateRange(range));
    }
    @Test void foreignCompanyRoleDoesNotGrantWorkOrderAccess() {
        User user=new User();Company mine=new Company();mine.setId(1L);user.setCompany(mine);
        Role role=new Role();role.setViewPermissions(Set.of(PermissionEntity.WORK_ORDERS));role.setViewOtherPermissions(Set.of(PermissionEntity.WORK_ORDERS));user.setRole(role);
        WorkOrder order=new WorkOrder();Company other=new Company();other.setId(2L);order.setCompany(other);
        assertFalse(order.canBeViewedBy(user));
    }
}
