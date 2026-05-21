package com.hmdp.tool;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ShopTypeTool {

    private static final Map<String, Integer> TYPE_MAP = new HashMap<>();

    static {
        TYPE_MAP.put("火锅", 1);
        TYPE_MAP.put("日料", 2);
        TYPE_MAP.put("烧烤", 3);
        TYPE_MAP.put("奶茶", 4);
    }

    public Integer resolveTypeId(String keyword) {
        if (keyword == null) {
            return null;
        }
        return TYPE_MAP.get(keyword);
    }
}