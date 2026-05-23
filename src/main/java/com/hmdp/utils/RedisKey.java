package com.hmdp.utils;

public enum RedisKey {
    LOGIN_CODE("login:code:", 2L),
    LOGIN_TOKEN("login:token:", 36000L),
    CACHE_SHOP("cache:shop:", 30L),
    CACHE_NULL("cache:null:", 2L),
    LOCK_SHOP("lock:shop:", 10L),
    SECKILL_STOCK("seckill:stock:", null),
    SECKILL_ORDER("seckill:order:", null),
    BLOG_LIKED("blog:liked:", null),
    FEED("feed:", null),
    SHOP_GEO("shop:geo:", null),
    USER_SIGN("sign:", null);

    private final String prefix;
    private final Long ttlMinutes;

    RedisKey(String prefix, Long ttlMinutes) {
        this.prefix = prefix;
        this.ttlMinutes = ttlMinutes;
    }

    public String key(Object id) { return prefix + id; }
    public String prefix() { return prefix; }
    public Long ttlMinutes() { return ttlMinutes; }
}
