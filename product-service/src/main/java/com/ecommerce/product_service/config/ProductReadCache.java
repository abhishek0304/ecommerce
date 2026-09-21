package com.ecommerce.product_service.config;
import com.ecommerce.product_service.dto.ProductResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
@Component
public class ProductReadCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final boolean enabled;
    public ProductReadCache(StringRedisTemplate redis, ObjectMapper json,
            @Value("${app.cache.products.enabled:false}") boolean enabled) {
        this.redis = redis; this.json = json; this.enabled = enabled;
    }
    public ProductResponse get(Long id, Long version, Supplier<ProductResponse> loader) {
        if (!enabled) return loader.get();
        String key = "ecommerce:product:v1:" + id + ":" + version;
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) return json.readValue(cached, ProductResponse.class);
        } catch (Exception ignored) { /* Redis is optional; read the database on failure. */ }
        ProductResponse result = loader.get();
        try { redis.opsForValue().set(key, json.writeValueAsString(result), Duration.ofMinutes(2)); }
        catch (Exception ignored) { /* Old versions expire automatically. */ }
        return result;
    }
}
