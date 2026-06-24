package com.zdc.order.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Best-effort distributed lock backed by Redis {@code SET key val NX EX ttl}.
 * Convention {@code order:agent:{id}} TTL 10s, used to dampen double-click order
 * creation (BR-006). Degrades to a permissive no-op when Redis is absent (tests),
 * since it is a mitigation, not a correctness guarantee.
 */
@Component
public class LockService {

    private static final Logger log = LoggerFactory.getLogger(LockService.class);

    private final ObjectProvider<StringRedisTemplate> redis;

    public LockService(ObjectProvider<StringRedisTemplate> redis) {
        this.redis = redis;
    }

    /** @return true if the lock was acquired (or Redis is unavailable). */
    public boolean acquire(String key, Duration ttl) {
        StringRedisTemplate template = redis.getIfAvailable();
        if (template == null) {
            return true;
        }
        Boolean ok = template.opsForValue().setIfAbsent(key, "1", ttl);
        return Boolean.TRUE.equals(ok);
    }

    public void release(String key) {
        StringRedisTemplate template = redis.getIfAvailable();
        if (template == null) {
            return;
        }
        try {
            template.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Failed to release lock {}: {}", key, ex.getMessage());
        }
    }
}
