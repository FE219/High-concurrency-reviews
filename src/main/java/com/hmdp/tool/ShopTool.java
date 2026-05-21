package com.hmdp.tool;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.tool.ShopSimpleDTO;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ShopTool {

    @Resource
    private IShopService shopService;

    /**
     * 根据店铺ID查询店铺
     */
    public ShopSimpleDTO getShopById(Long shopId) {
        if (shopId == null) {
            return null;
        }
        Shop shop = shopService.getShopEntityById(shopId);
        if (shop == null) {
            return null;
        }
        return toSimpleDTO(shop);
    }

    /**
     * 按大类 + 坐标查询附近店铺
     * x=经度，y=纬度
     */
    public List<ShopSimpleDTO> searchNearbyByType(Integer typeId, Double x, Double y, Integer current) {
        if (typeId == null) {
            return Collections.emptyList();
        }
        List<Shop> shops = shopService.queryNearbyShopEntitiesByType(typeId, current == null ? 1 : current, x, y);
        if (CollUtil.isEmpty(shops)) {
            return Collections.emptyList();
        }
        return shops.stream().map(this::toSimpleDTO).collect(Collectors.toList());
    }

    /**
     * 按关键词搜索店铺
     */
    public List<ShopSimpleDTO> searchByKeyword(String keyword, Integer current) {
        if (StrUtil.isBlank(keyword)) {
            return Collections.emptyList();
        }
        List<Shop> shops = shopService.queryShopEntitiesByKeyword(keyword, current == null ? 1 : current);
        if (CollUtil.isEmpty(shops)) {
            return Collections.emptyList();
        }
        return shops.stream().map(this::toSimpleDTO).collect(Collectors.toList());
    }

    /**
     * 搜第一家匹配店铺，适合 AI 场景快速定位
     */
    public ShopSimpleDTO searchFirstByKeyword(String keyword) {
        List<ShopSimpleDTO> list = searchByKeyword(keyword, 1);
        if (CollUtil.isEmpty(list)) {
            return null;
        }
        return list.get(0);
    }

    /**
     * 按关键词对店铺进行二次过滤
     * 用于“火锅/日料/烧烤/茶餐厅”这种不在 tb_shop_type 中的细分类
     */
    public List<ShopSimpleDTO> filterBySubCategoryKeyword(List<ShopSimpleDTO> shops, String keyword) {
        if (CollUtil.isEmpty(shops) || StrUtil.isBlank(keyword)) {
            return shops;
        }

        return shops.stream()
                .filter(shop -> matchSubCategory(shop.getName(), keyword))
                .collect(Collectors.toList());
    }

    private boolean matchSubCategory(String shopName, String keyword) {
        if (StrUtil.isBlank(shopName) || StrUtil.isBlank(keyword)) {
            return false;
        }
        String name = shopName.toLowerCase();

        if (keyword.contains("火锅")) {
            return name.contains("火锅") || name.contains("涮") || name.contains("羊蝎子") || name.contains("锅");
        }
        if (keyword.contains("日料") || keyword.contains("寿司") || keyword.contains("刺身")) {
            return name.contains("日料") || name.contains("寿司") || name.contains("刺身");
        }
        if (keyword.contains("烧烤") || keyword.contains("烤肉")) {
            return name.contains("烧烤") || name.contains("烤肉");
        }
        if (keyword.contains("茶餐厅") || keyword.contains("冰厅")) {
            return name.contains("茶餐厅") || name.contains("冰厅");
        }
        if (keyword.contains("咖啡")) {
            return name.contains("咖啡") || name.contains("coffee");
        }
        if (keyword.contains("奶茶")) {
            return name.contains("奶茶") || name.contains("茶");
        }

        return name.contains(keyword.toLowerCase());
    }

    private ShopSimpleDTO toSimpleDTO(Shop shop) {
        ShopSimpleDTO dto = new ShopSimpleDTO();
        dto.setShopId(shop.getId());
        dto.setName(shop.getName());
        dto.setAddress(shop.getAddress());
        dto.setX(shop.getX());
        dto.setY(shop.getY());
        dto.setTypeId(shop.getTypeId());
        dto.setArea(shop.getArea());
        dto.setOpenHours(shop.getOpenHours());

        // avg_price 数据库单位就是元
        if (shop.getAvgPrice() != null) {
            dto.setAvgPrice(shop.getAvgPrice().doubleValue());
        }

        // score 数据库存的是乘10后的整数，例如49表示4.9
        if (shop.getScore() != null) {
            dto.setScore(shop.getScore() / 10.0);
        }

        // distance 来自 GEO 查询回填，不是所有场景都有
        if (shop.getDistance() != null) {
            dto.setDistance(shop.getDistance().doubleValue());
        }

        return dto;
    }
}