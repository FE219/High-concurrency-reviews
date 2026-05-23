package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
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

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
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


    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }
}
