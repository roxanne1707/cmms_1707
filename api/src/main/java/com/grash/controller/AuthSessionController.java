package com.grash.controller;

import com.grash.dto.SuccessResponse;
import com.grash.exception.CustomException;
import com.grash.model.User;
import com.grash.security.CurrentUser;
import com.grash.security.JwtTokenProvider;
import com.grash.security.SessionRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/auth/sessions")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AuthSessionController {
    private final SessionRegistry sessions;
    private final JwtTokenProvider jwt;
    public record SessionList(List<SessionRegistry.SessionView> sessions, boolean legacySession, int limit) {}

    @GetMapping
    public SessionList list(@CurrentUser User user,HttpServletRequest request) {
        UUID current=current(request);
        return new SessionList(sessions.list(user.getId(),current),current==null,100);
    }
    @PostMapping("/logout-current")
    public SuccessResponse logoutCurrent(@CurrentUser User user,HttpServletRequest request) {
        UUID id=current(request);
        if(id==null) throw new CustomException("Legacy session: refresh the token or sign out all devices",HttpStatus.CONFLICT);
        sessions.revoke(id,user.getId());
        return new SuccessResponse(true,"Current device signed out");
    }
    @PostMapping("/logout-all")
    public SuccessResponse logoutAll(@CurrentUser User user,HttpServletRequest request) {
        current(request); // API keys must not administer interactive login sessions.
        sessions.revokeAll(user.getId());
        return new SuccessResponse(true,"All devices signed out");
    }
    @DeleteMapping("/{id}")
    public SuccessResponse revoke(@PathVariable UUID id,@CurrentUser User user,HttpServletRequest request) {
        current(request);
        sessions.revoke(id,user.getId());
        return new SuccessResponse(true,"Session revoked");
    }
    private UUID current(HttpServletRequest request) {
        // Session administration uses an explicit bearer header, not ambient Swagger cookies.
        String header=request.getHeader("Authorization");
        if(header==null || !header.startsWith("Bearer ")) throw new CustomException("A personal Bearer JWT is required",HttpStatus.UNAUTHORIZED);
        String token=jwt.resolveToken(request);
        if(token==null) throw new CustomException("A personal JWT is required",HttpStatus.UNAUTHORIZED);
        return jwt.getSessionId(token);
    }
}
