package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);


        if (shop == null){
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

 /*   private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);*/

//    public Shop queryWithLogicalExpire(Long id){
//        String key = CACHE_SHOP_KEY + id;
//        //1.从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断是否存在
//        if (StrUtil.isBlank(shopJson)){
//            //3.存在，直接返回
//            return null;
//        }
//        //4.命中，需要先把json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//
//        LocalDateTime expireTime = redisData.getExpireTime();
//        //5.判断是否过期
//        if (expireTime.isAfter(LocalDateTime.now())){
//            //5.1未过期，直接返回店铺信息
//            return shop;
//        }
//        //5.2 已过期，需要缓存重建
//        //6.缓存重建
//        //6.1获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//        //6.2判断是否获取成功
//        if (isLock){
//            //6.3成功，开启独立线程，实现缓存重建
//            if (StrUtil.isNotBlank(shopJson)){
//                return JSONUtil.toBean(shopJson, Shop.class);
//            }
//            CACHE_REBUILD_EXECUTOR.submit(() ->{
//                try {
//                    this.saveShop2Redis(id, 20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    //释放锁
//                    unLock(lockKey);
//                }
//            });
//
//        }
//        //6.4返回过期的商铺信息
//        return shop;
//    }


//    public Shop queryWithMutex(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//
//        // 1. 查缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        if (StrUtil.isNotBlank(shopJson)) {
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        // 命中空值（""），直接返回 null，防穿透
//        if (shopJson != null) {
//            return null;
//        }
//
//        String lockKey = "lock:shop:" + id;
//        boolean isLock = false;
//        try {
//            // 2. while 自旋抢锁（抢不到就睡一会再试）
//            while (!(isLock = tryLock(lockKey))) {
//                Thread.sleep(50);
//
//                // 建议：等待过程中顺便再查一次缓存
//                // 因为可能别的线程已经重建好了缓存，你就不用继续抢锁了
//                shopJson = stringRedisTemplate.opsForValue().get(key);
//                if (StrUtil.isNotBlank(shopJson)) {
//                    return JSONUtil.toBean(shopJson, Shop.class);
//                }
//                if (shopJson != null) {
//                    return null;
//                }
//            }
//
//            // 3. 拿到锁后“双重检查”缓存（避免重复重建）
//            shopJson = stringRedisTemplate.opsForValue().get(key);
//            if (StrUtil.isNotBlank(shopJson)) {
//                return JSONUtil.toBean(shopJson, Shop.class);
//            }
//            if (shopJson != null) {
//                return null;
//            }
//
//            // 4. 查数据库
//            Shop shop = getById(id);
//
//            // 5. 数据库不存在：缓存空值（短 TTL）防穿透
//            if (shop == null) {
//                stringRedisTemplate.opsForValue()
//                        .set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//
//            // 6. 写入 Redis（正常 TTL）
//            stringRedisTemplate.opsForValue()
//                    .set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//            return shop;
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            throw new RuntimeException(e);
//        } finally {
//            // 7. 只有真正拿到锁，才释放锁
//            if (isLock) {
//                unLock(lockKey);
//            }
//        }
//    }
/*
    public void saveShop2Redis(Long id, Long expireSeconds){
        //1.查询店铺数据
        Shop shop = getById(id);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }*/

//    public Shop queryWithPassThrough(Long id){
//        String key = CACHE_SHOP_KEY + id;
//        //1.从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断是否存在
//        if (StrUtil.isNotBlank(shopJson)){
//            //3.存在，直接返回
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //命中的是否是空值
//        if (shopJson != null){
//            //返回一个错误信息
//            return null;
//        }
//        //4.不存在，根据id查询数据库
//        Shop shop = getById(id);
//        //5.不存在，返回错误
//        if (shop == null){
//            //将空值写入Redis
//            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        //6.存在，写入redis
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//        return shop;
//    }

//    private boolean tryLock(String key){
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    private void unLock(String key){
//    stringRedisTemplate.delete(key);
//    }


    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null){
            return Result.fail("店铺id不为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);

        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //判断是否需要根据坐标查询
        if (x == null || y == null){
            //不需要坐标查询，按数据库查询
            Page<Shop> page = query().eq("type_id", typeId).page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        //计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        //查询redis、按照距离排序、分页 结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() //GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                .search(key, GeoReference.fromCoordinate(x, y), new Distance(5000, RedisGeoCommands.DistanceUnit.METERS),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
        //解析出id
        if (results == null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        if (content.size() <= from){
            return Result.ok(Collections.emptyList());
        }
        Map<String , Distance> distanceMap = new HashMap<>(content.size());
        List<Long> ids = new ArrayList<>(content.size());
        //截取from-end 的部分

        content.stream().skip(from).forEach(result ->{
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            //获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        //根据id查询shop
        String idStr = StrUtil.join("," , ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr +")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        //返回
        return Result.ok(shops);
    }

    @Override
    public Shop getShopEntityById(Long shopId) {
        return getById(shopId);
    }

    @Override
    public List<Shop> queryNearbyShopEntitiesByType(Integer typeId, Integer current, Double x, Double y) {
        // ---------- 逻辑完全复用原 queryShopByType ----------
        if (x == null || y == null) {
            // 无坐标时，按数据库分页查询
            Page<Shop> page = lambdaQuery()
                    .eq(typeId != null, Shop::getTypeId, typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return page.getRecords();
        }

        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        String geoKey = SHOP_GEO_KEY + typeId;
        // GEOSEARCH：按坐标+半径查附近店铺，并按距离排序
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        geoKey,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000, RedisGeoCommands.DistanceUnit.METERS), // 半径5km
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                                .includeDistance()   // 返回距离
                                .limit(end)          // 限制结果数量
                );

        if (results == null || results.getContent().isEmpty()) {
            return Collections.emptyList();
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        if (content.size() <= from) {
            return Collections.emptyList();
        }

        // 提取 shopId 和 距离
        List<Long> shopIds = new ArrayList<>();
        Map<String, Distance> distanceMap = new HashMap<>();
        content.stream()
                .skip(from)               // 跳过前 `from` 条
                .forEach(result -> {
                    String shopIdStr = result.getContent().getName();
                    shopIds.add(Long.valueOf(shopIdStr));
                    distanceMap.put(shopIdStr, result.getDistance());
                });

        if (shopIds.isEmpty()) return Collections.emptyList();

        // 根据 shopId 批量查询店铺，并按距离排序
        String idStr = StrUtil.join("," , shopIds);
        List<Shop> shops = lambdaQuery()
                .in(Shop::getId, shopIds)
                .last("ORDER BY FIELD(id, " + idStr + ")") // 按传入顺序排序
                .list();

        // 为每个 Shop 设置距离（单位：米）
        shops.forEach(shop -> {
            Distance dist = distanceMap.get(shop.getId().toString());
            if (dist != null) {
                shop.setDistance(dist.getValue()); // **必须在 Shop 实体中添加 `distance` 字段！**
            }
        });

        return shops;
    }

    @Override
    public List<Shop> queryShopEntitiesByKeyword(String keyword, Integer current) {
        if (StrUtil.isBlank(keyword)) {
            return Collections.emptyList();
        }

        int pageNo = current == null || current < 1 ? 1 : current;

        // 先精确匹配
        List<Shop> exactList = query()
                .eq("name", keyword)
                .last("limit " + SystemConstants.DEFAULT_PAGE_SIZE)
                .list();
        if (!exactList.isEmpty()) {
            return exactList;
        }

        // 再模糊匹配
        Page<Shop> page = query()
                .like("name", keyword)
                .page(new Page<>(pageNo, SystemConstants.DEFAULT_PAGE_SIZE));

        return page.getRecords();
    }
}
