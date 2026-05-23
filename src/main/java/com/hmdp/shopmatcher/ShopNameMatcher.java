package com.hmdp.shopmatcher;

import cn.hutool.core.util.StrUtil;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopNameMatcher {

    private final IShopService shopService;

    private volatile Map<String, Long> nameIndex = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refresh();
    }

    public void refresh() {
        List<Shop> shops = shopService.list();
        Map<String, Long> index = new ConcurrentHashMap<>();
        for (Shop shop : shops) {
            if (StrUtil.isNotBlank(shop.getName())) {
                index.put(shop.getName().toLowerCase(), shop.getId());
            }
        }
        this.nameIndex = index;
        log.info("ShopNameMatcher refreshed: {} shops loaded", index.size());
    }

    /**
     * Match a message against known shop names.
     * Returns shop ID if a known shop name is found in the message,
     * preferring longer name matches to avoid partial matches.
     */
    public Long match(String message) {
        if (StrUtil.isBlank(message)) return null;
        String lower = message.toLowerCase();

        return nameIndex.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByKey(
                        Comparator.comparingInt(String::length).reversed()))
                .filter(e -> lower.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * Match and return the shop name (not just ID).
     */
    public String matchName(String message) {
        if (StrUtil.isBlank(message)) return null;
        String lower = message.toLowerCase();

        return nameIndex.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .filter(lower::contains)
                .findFirst()
                .orElse(null);
    }
}
