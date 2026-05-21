package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;


    private static final String CACHE_KEY = "cache:shopType:list";
    private static final Long CACHE_TTL_MINUTES = 30L;

    @Override
    public Result queryTypeListCache() {
        String json = stringRedisTemplate.opsForValue().get(CACHE_KEY);
        if (StrUtil.isNotBlank(json)){
            List<ShopType> list = JSONUtil.toList(JSONUtil.parseArray(json), ShopType.class);
            return Result.ok(list);
        }
        List<ShopType> typeList = this.query().orderByAsc("sort").list();

        stringRedisTemplate.opsForValue().set(
                CACHE_KEY,
                JSONUtil.toJsonStr(typeList),
                CACHE_TTL_MINUTES,
                TimeUnit.MINUTES
        );

        return Result.ok(typeList);
    }
}
