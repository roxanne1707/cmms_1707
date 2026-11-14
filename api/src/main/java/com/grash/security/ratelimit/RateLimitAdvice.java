package com.grash.security.ratelimit;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitAdvice {
    @ExceptionHandler(RateLimitRejected.class)
    public ResponseEntity<Map<String,Object>> rejected(RateLimitRejected failure) {
        var d=failure.decision();
        return ResponseEntity.status(d.status()).header("Retry-After",Long.toString(d.retryAfterSeconds())).body(d.body());
    }
}
