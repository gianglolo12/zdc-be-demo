package com.zdc.order.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Thin JSON list cache over Redis with TTL (FS-001/FS-002, TTL 30d). Degrades to
 * a no-op when Redis is absent, so the gateways still work (just without caching).
 */
@Component
public class OrderCache {

    private static final Logger log = LoggerFactory.getLogger(OrderCache.class);

    private final ObjectProvider<StringRedisTemplate> redis;
    private final ObjectMapper mapper;

    public OrderCache(ObjectProvider<StringRedisTemplate> redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public <T> Optional<List<T>> getList(String key, Class<T> type) {
        StringRedisTemplate template = redis.getIfAvailable();
        if (template == null) {
            return Optional.empty();
        }
        try {
            String json = template.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            CollectionType ct = mapper.getTypeFactory().constructCollectionType(List.class, type);
            return Optional.of(mapper.readValue(json, ct));
        } catch (Exception ex) {
            log.warn("Cache read failed for {}: {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    public void putList(String key, List<?> value, Duration ttl) {
        StringRedisTemplate template = redis.getIfAvailable();
        if (template == null) {
            return;
        }
        try {
            template.opsForValue().set(key, mapper.writeValueAsString(value), ttl);
        } catch (Exception ex) {
            log.warn("Cache write failed for {}: {}", key, ex.getMessage());
        }
    }
}
