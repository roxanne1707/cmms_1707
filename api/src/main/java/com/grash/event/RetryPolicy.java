package com.grash.event;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public final class RetryPolicy {
    private RetryPolicy() {}
    public static Duration delay(int attempt) {
        long[] seconds = {1, 5, 30, 120, 300};
        long base = seconds[Math.min(Math.max(attempt, 0), seconds.length - 1)];
        return Duration.ofMillis(base * 1000 + ThreadLocalRandom.current().nextLong(251));
    }
    public static String error(Throwable failure) {
        // Exception messages can contain credentials, SMTP addresses or endpoint URLs.
        return failure.getClass().getSimpleName();
    }
}
