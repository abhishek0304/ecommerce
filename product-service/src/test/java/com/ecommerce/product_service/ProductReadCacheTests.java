package com.ecommerce.product_service;
import com.ecommerce.product_service.config.ProductReadCache;
import com.ecommerce.product_service.dto.ProductResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.*;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProductReadCacheTests {
    @Test void versionChangeBypassesOldStockAndRedisFailureFallsBack() throws Exception {
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String,String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var json = new ObjectMapper().findAndRegisterModules();
        var before = new ProductResponse(1L,"A","Product",null,BigDecimal.TEN,10,"Test",true,null,null);
        var after = new ProductResponse(1L,"A","Product",null,BigDecimal.TEN,8,"Test",true,null,null);
        when(values.get("ecommerce:product:v1:1:0")).thenReturn(json.writeValueAsString(before));
        var cache = new ProductReadCache(redis,json,true);
        var loads = new AtomicInteger();
        assertEquals(10, cache.get(1L,0L,()->{loads.incrementAndGet();return after;}).stockQuantity());
        assertEquals(0,loads.get());
        assertEquals(8,cache.get(1L,1L,()->after).stockQuantity());
        when(values.get("ecommerce:product:v1:1:2")).thenThrow(new IllegalStateException("Redis down"));
        assertEquals(after,cache.get(1L,2L,()->after));
    }
}

