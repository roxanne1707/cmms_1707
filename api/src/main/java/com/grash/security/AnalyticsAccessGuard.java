package com.grash.security;

import com.grash.dto.DateRange;
import com.grash.exception.CustomException;
import com.grash.model.User;
import com.grash.model.enums.PermissionEntity;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Controller response caching must never short-circuit fresh authorization.
 * Cross-instance freshness for hot aggregates belongs to the versioned Redis query module. */
@Aspect @Component @RequiredArgsConstructor
public class AnalyticsAccessGuard {
    private final FreshMaintenanceActor actors;
    @Around("execution(public * com.grash.controller.analytics.*.*(..))")
    public Object authorize(ProceedingJoinPoint call) throws Throwable {
        Object[] args=call.getArgs().clone();
        User user=null;
        for(int i=0;i<args.length;i++) {
            if(args[i] instanceof User identity) { user=actors.load(identity);args[i]=user; }
            if(args[i] instanceof DateRange range) validateRange(range);
        }
        if(user==null) throw new CustomException("Authentication required",HttpStatus.UNAUTHORIZED);
        String controller=call.getTarget().getClass().getSimpleName();
        String method=call.getSignature().getName();
        PermissionEntity resource=controller.startsWith("Asset")?PermissionEntity.ASSETS:
                controller.startsWith("Part")?PermissionEntity.PARTS_AND_MULTIPARTS:
                controller.startsWith("Request")?PermissionEntity.REQUESTS:PermissionEntity.WORK_ORDERS;
        if(!user.getRole().getViewPermissions().contains(resource)) denied();
        boolean personal=controller.startsWith("User") || method.equals("getMobileOverview") || method.equals("getMobileExtendedStats");
        boolean oneAsset=controller.startsWith("Asset") && method.equals("getDateRangeOverview");
        if(!personal && !oneAsset) {
            if(!user.canSeeAnalytics() || !user.getRole().getViewOtherPermissions().contains(resource)) denied();
        }
        if(method.equals("getMobileOverview")) {
            boolean assigned=false;for(Object arg:args) if(arg instanceof Boolean b) assigned=b;
            if(!assigned && !user.getRole().getViewOtherPermissions().contains(PermissionEntity.WORK_ORDERS)) denied();
        }
        if(method.equals("getMobileExtendedStats") && !user.getRole().getViewOtherPermissions().contains(PermissionEntity.WORK_ORDERS)) denied();
        return call.proceed(args);
    }
    public static void validateRange(DateRange range) {
        if(range.getStart()==null || range.getEnd()==null || !range.getStart().before(range.getEnd())
                || range.getEnd().getTime()-range.getStart().getTime()>3660L*86400000L)
            throw new CustomException("Use a valid analytics interval of at most ten years",HttpStatus.BAD_REQUEST);
    }
    private static void denied(){throw new CustomException("Access denied",HttpStatus.FORBIDDEN);}
}
