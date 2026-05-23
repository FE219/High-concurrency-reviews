package com.hmdp.config;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmupRunner implements ApplicationRunner {

    private final IShopService shopService;
    private final CacheClient cacheClient;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting cache warmup...");
        List<Shop> shops = shopService.list();
        int count = 0;
        for (Shop shop : shops) {
            try {
                cacheClient.set(CACHE_SHOP_KEY + shop.getId(), shop,
                        CACHE_SHOP_TTL, TimeUnit.MINUTES);
                count++;
            } catch (Exception e) {
                log.warn("Failed to warm cache for shopId={}: {}", shop.getId(), e.getMessage());
            }
        }
        log.info("Cache warmup complete: {}/{} shops loaded", count, shops.size());
    }
}
