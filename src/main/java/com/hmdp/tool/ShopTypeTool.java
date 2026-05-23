package com.hmdp.tool;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    /**
     * Return all available shop type category names.
     */
    public List<String> getAllTypes() {
        return new ArrayList<>(TYPE_MAP.keySet());
    }
}