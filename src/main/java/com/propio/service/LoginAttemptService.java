package com.propio.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to protect against brute-force login attacks and credential stuffing.
 * Tracks failed authentication attempts per user email.
 * Locks the account identifier for 15 minutes after 5 consecutive failed attempts.
 */
@Service
@Slf4j
public class LoginAttemptService {

    public static final int MAX_ATTEMPTS = 5;
    public static final long LOCK_TIME_DURATION_MILLIS = 15 * 60 * 1000L; // 15 minutes lockout

    private final Map<String, Integer> attemptsCache = new ConcurrentHashMap<>();
    private final Map<String, Long> lockoutExpiryCache = new ConcurrentHashMap<>();

    /**
     * Clears failed attempt tracking upon successful authentication.
     */
    public void loginSucceeded(String key) {
        String cleanKey = normalizeKey(key);
        attemptsCache.remove(cleanKey);
        lockoutExpiryCache.remove(cleanKey);
        log.debug("Reset login attempt counters for identifier: {}", cleanKey);
    }

    /**
     * Increments failed attempt counter and applies lockout when threshold is reached.
     */
    public void loginFailed(String key) {
        String cleanKey = normalizeKey(key);
        if (isBlocked(cleanKey)) {
            return;
        }

        int attempts = attemptsCache.getOrDefault(cleanKey, 0) + 1;
        attemptsCache.put(cleanKey, attempts);
        log.warn("Failed authentication attempt #{}/{} for: {}", attempts, MAX_ATTEMPTS, cleanKey);

        if (attempts >= MAX_ATTEMPTS) {
            long lockoutUntil = System.currentTimeMillis() + LOCK_TIME_DURATION_MILLIS;
            lockoutExpiryCache.put(cleanKey, lockoutUntil);
            log.warn("SECURITY LOCKOUT: Account '{}' exceeded {} failed attempts. Locked until: {}", 
                    cleanKey, MAX_ATTEMPTS, Instant.ofEpochMilli(lockoutUntil));
        }
    }

    /**
     * Checks if the account identifier is currently locked out.
     */
    public boolean isBlocked(String key) {
        String cleanKey = normalizeKey(key);
        Long lockoutUntil = lockoutExpiryCache.get(cleanKey);
        if (lockoutUntil == null) {
            return false;
        }

        if (System.currentTimeMillis() > lockoutUntil) {
            // Lockout period has elapsed; clean up
            lockoutExpiryCache.remove(cleanKey);
            attemptsCache.remove(cleanKey);
            log.info("Lockout period expired for: {}", cleanKey);
            return false;
        }
        return true;
    }

    /**
     * Returns remaining lockout duration in minutes for user-facing feedback.
     */
    public int getRemainingLockoutMinutes(String key) {
        String cleanKey = normalizeKey(key);
        Long lockoutUntil = lockoutExpiryCache.get(cleanKey);
        if (lockoutUntil == null || System.currentTimeMillis() > lockoutUntil) {
            return 0;
        }
        long diff = lockoutUntil - System.currentTimeMillis();
        return Math.max(1, (int) Math.ceil(diff / (60.0 * 1000.0)));
    }

    public int getAttempts(String key) {
        return attemptsCache.getOrDefault(normalizeKey(key), 0);
    }

    public void reset() {
        attemptsCache.clear();
        lockoutExpiryCache.clear();
    }

    private String normalizeKey(String key) {
        return (key == null || key.isBlank()) ? "unknown" : key.trim().toLowerCase();
    }
}
