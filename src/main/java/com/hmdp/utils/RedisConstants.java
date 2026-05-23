package com.hmdp.utils;

/**
 * @deprecated Use {@link RedisKey} enum instead.
 * Kept for backward compatibility during migration.
 */
@Deprecated
public class RedisConstants {
    public static final String LOGIN_CODE_KEY = RedisKey.LOGIN_CODE.prefix();
    public static final Long LOGIN_CODE_TTL = RedisKey.LOGIN_CODE.ttlMinutes();
    public static final String LOGIN_USER_KEY = RedisKey.LOGIN_TOKEN.prefix();
    public static final Long LOGIN_USER_TTL = RedisKey.LOGIN_TOKEN.ttlMinutes();

    public static final Long CACHE_NULL_TTL = RedisKey.CACHE_NULL.ttlMinutes();

    public static final Long CACHE_SHOP_TTL = RedisKey.CACHE_SHOP.ttlMinutes();
    public static final String CACHE_SHOP_KEY = RedisKey.CACHE_SHOP.prefix();

    public static final String LOCK_SHOP_KEY = RedisKey.LOCK_SHOP.prefix();
    public static final Long LOCK_SHOP_TTL = RedisKey.LOCK_SHOP.ttlMinutes();

    public static final String SECKILL_STOCK_KEY = RedisKey.SECKILL_STOCK.prefix();
    public static final String BLOG_LIKED_KEY = RedisKey.BLOG_LIKED.prefix();
    public static final String FEED_KEY = RedisKey.FEED.prefix();
    public static final String SHOP_GEO_KEY = RedisKey.SHOP_GEO.prefix();
    public static final String USER_SIGN_KEY = RedisKey.USER_SIGN.prefix();
}
