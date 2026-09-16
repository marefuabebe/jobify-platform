package com.webapp.jobportal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.lang.NonNull;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    // Limits
    private static final int LOGIN_CAPACITY = 20;
    private static final int PAYMENT_CAPACITY = 15;
    private static final Duration REFILL_DURATION = Duration.ofMinutes(1);

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Only rate-limit POST submissions to /login (credential checks) and payment requests
        boolean isLoginAttempt = "POST".equalsIgnoreCase(method) && ("/login".equals(path) || path.startsWith("/login?"));
        boolean isPaymentRequest = path.startsWith("/payment/");

        if (isLoginAttempt || isPaymentRequest) {
            String ip = getClientIp(request);
            String endpointType = isLoginAttempt ? "LOGIN" : "PAYMENT";
            Bucket bucket = buckets.computeIfAbsent(ip + ":" + endpointType, k -> createBucket(isLoginAttempt));

            if (!bucket.tryConsume()) {
                response.setStatus(429); // Too Many Requests
                response.setContentType("text/plain");
                response.getWriter().write("Too many requests. Please try again in a minute.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.trim().isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Bucket createBucket(boolean isLogin) {
        int capacity = isLogin ? LOGIN_CAPACITY : PAYMENT_CAPACITY;
        return new Bucket(capacity, capacity, REFILL_DURATION);
    }

    // Simple Token Bucket implementation
    private static class Bucket {
        private final int capacity;
        private int tokens;
        private final Duration refillDuration;
        private Instant lastRefill;

        public Bucket(int capacity, int tokens, Duration refillDuration) {
            this.capacity = capacity;
            this.tokens = tokens;
            this.refillDuration = refillDuration;
            this.lastRefill = Instant.now();
        }

        public synchronized boolean tryConsume() {
            refill();
            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }

        private void refill() {
            Instant now = Instant.now();
            // Better logic: standard token bucket refills X tokens per period.
            // Simplified: Refill to full if duration passed.
            // Even better: Proportional refill.

            if (Duration.between(lastRefill, now).compareTo(refillDuration) > 0) {
                tokens = capacity;
                lastRefill = now;
            }
        }
    }
}
