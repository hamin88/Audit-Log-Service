package com.example.auditlog.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuditRequestGuardFilter extends OncePerRequestFilter {

    private final AuditApiProperties properties;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, String> idempotencyCache = new ConcurrentHashMap<>();

    public AuditRequestGuardFilter(AuditApiProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"/audit/events".equals(request.getRequestURI()) || !"POST".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        if (body.length > properties.maxRequestBytes()) {
            response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Request body exceeds configured limit");
            return;
        }

        String principal = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : request.getRemoteAddr();
        if (!allow(principal)) {
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Rate limit exceeded");
            return;
        }

        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String cacheKey = principal + ":" + idempotencyKey.trim();
            String bodyHash = Integer.toHexString(java.util.Arrays.hashCode(body));
            String previous = idempotencyCache.putIfAbsent(cacheKey, bodyHash);
            if (previous != null && !previous.equals(bodyHash)) {
                response.sendError(HttpStatus.CONFLICT.value(), "Idempotency-Key was reused with a different request body");
                return;
            }
        }

        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private boolean allow(String principal) {
        AuditApiProperties.RateLimit rateLimit = properties.rateLimit();
        Bucket bucket = buckets.computeIfAbsent(principal, key -> new Bucket(rateLimit.maxRequests(), rateLimit.window()));
        return bucket.tryConsume();
    }

    private static final class Bucket {
        private final long capacity;
        private final long refillIntervalMillis;
        private long tokens;
        private long lastRefillEpochMillis;

        private Bucket(long capacity, Duration window) {
            this.capacity = capacity;
            this.refillIntervalMillis = Math.max(1L, window.toMillis());
            this.tokens = capacity;
            this.lastRefillEpochMillis = Instant.now().toEpochMilli();
        }

        private synchronized boolean tryConsume() {
            long now = Instant.now().toEpochMilli();
            if (now - lastRefillEpochMillis >= refillIntervalMillis) {
                tokens = capacity;
                lastRefillEpochMillis = now;
            }
            if (tokens <= 0) {
                return false;
            }
            tokens--;
            return true;
        }
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(body);
            return new jakarta.servlet.ServletInputStream() {
                @Override
                public int read() {
                    return inputStream.read();
                }

                @Override
                public boolean isFinished() {
                    return inputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(jakarta.servlet.ReadListener readListener) {
                }
            };
        }
    }
}
