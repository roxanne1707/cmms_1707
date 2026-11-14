package com.grash.security.ratelimit;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

public record RateLimitDecision(int status,long retryAfterSeconds) {
    public boolean allowed() { return status==200; }
    public String code() { return status==429?"MAINTENANCE_RATE_LIMITED":"MAINTENANCE_LIMIT_UNAVAILABLE"; }
    public Map<String,Object> body() { return Map.of("error",code(),"message",status==429?"Too many requests. Retry later.":"Request protection is temporarily unavailable. Retry later.","retryAfterSeconds",retryAfterSeconds); }
    public void write(HttpServletResponse response) throws IOException {
        response.setStatus(status);response.setHeader("Retry-After",Long.toString(retryAfterSeconds));
        response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(response.getWriter(),body());
    }
}
