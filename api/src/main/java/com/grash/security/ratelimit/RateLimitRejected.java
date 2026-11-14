package com.grash.security.ratelimit;

public class RateLimitRejected extends RuntimeException {
    private final RateLimitDecision decision;
    public RateLimitRejected(RateLimitDecision decision) { super(decision.code());this.decision=decision; }
    public RateLimitDecision decision() { return decision; }
}
