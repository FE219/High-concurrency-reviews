package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.hmdp.utils.RedisKey;


@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;
    private final Cache<String, Object> localCache;

    public CacheClient(StringRedisTemplate stringRedisTemplate, Cache<String, Object> localCache) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.localCache = localCache;
    }
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        //1.从redis查询商铺缓存
        String Json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(Json)){
            //3.存在，直接返回
            return JSONUtil.toBean(Json, type);
        }
        //命中的是否是空值
        if (Json != null){
            //返回一个错误信息
            return null;
        }
        //4.不存在，根据id查询数据库
        R r = dbFallback.apply(id);
        //5.不存在，返回错误
        if (r == null){
            //将空值写入Redis
            stringRedisTemplate.opsForValue().set(key, "", RedisKey.CACHE_NULL.ttlMinutes(), TimeUnit.MINUTES);
            return null;
        }
        //6.存在，写入redis
        this.set(key, r, time, unit);

        return r;
    }

    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type,
            Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }

        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);

        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;  // not expired, return fresh data
        }

        // Expired — try to acquire lock
        String lockKey = RedisKey.LOCK_SHOP.prefix() + id;
        boolean isLock = tryLock(lockKey);
        if (!isLock) {
            return r;  // another thread is rebuilding, return stale data
        }

        try {
            // Double-check: another thread may have already rebuilt
            shopJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(shopJson)) {
                redisData = JSONUtil.toBean(shopJson, RedisData.class);
                if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                    return JSONUtil.toBean((JSONObject) redisData.getData(), type);
                }
            }

            // Rebuild in current thread (lock held by THIS thread)
            R r1 = dbFallback.apply(id);
            this.setWithLogicalExpire(key, r1, time, unit);
            return r1;
        } finally {
            unLock(lockKey);  // Release in SAME thread that acquired
        }
    }


    public void publishInvalidation(String key) {
        stringRedisTemplate.convertAndSend("cache:invalidate", key);
    }

    // ========== 二级缓存（L1 Caffeine + L2 Redis）= ==========

    /**
     * 二级缓存查询：L1(Caffeine) → L2(Redis) → DB
     *
     * @param keyPrefix      Redis key 前缀
     * @param id             业务 ID
     * @param type           返回值类型
     * @param dbFallback     数据库查询回调
     * @param redisTtl       Redis 过期时间
     * @param redisTtlUnit   Redis 过期时间单位
     * @param localTtlSeconds L1 本地缓存过期秒数（同时用于 Redis 空值缓存）
     */
    public <R, ID> R queryWithTwoLevel(
            String keyPrefix, ID id, Class<R> type,
            Function<ID, R> dbFallback,
            Long redisTtl, TimeUnit redisTtlUnit,
            long localTtlSeconds) {
        String key = keyPrefix + id;

        // ===== 1. 查 L1 本地缓存 =====
        Object cached = localCache.getIfPresent(key);
        if (cached != null) {
            if (cached == NullValue.INSTANCE) {
                return null;  // 穿透保护
            }
            log.debug("[L1] hit: key={}", key);
            return (R) cached;
        }

        // ===== 2. 查 L2 Redis =====
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            R r = JSONUtil.toBean(json, type);
            localCache.put(key, r);
            log.debug("[L2] hit, populated L1: key={}", key);
            return r;
        }
        // 命中 Redis 空值标记
        if (json != null) {
            localCache.put(key, NullValue.INSTANCE);
            return null;
        }

        // ===== 3. 查数据库 =====
        R r = dbFallback.apply(id);
        if (r == null) {
            // 双重穿透防护：Redis 空值 + L1 空值
            stringRedisTemplate.opsForValue().set(key, "",
                    localTtlSeconds, TimeUnit.SECONDS);
            localCache.put(key, NullValue.INSTANCE);
            log.debug("[DB] miss, cached null: key={}", key);
            return null;
        }

        // 写 L2 + L1
        this.set(key, r, redisTtl, redisTtlUnit);
        localCache.put(key, r);
        log.debug("[DB] hit, populated L1+L2: key={}", key);
        return r;
    }

    /**
     * 空值占位符，区别于缓存中真实存在的 null
     */
    private static final class NullValue {
        static final NullValue INSTANCE = new NullValue();
    }

    // ========== private helpers ==========

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }
}
