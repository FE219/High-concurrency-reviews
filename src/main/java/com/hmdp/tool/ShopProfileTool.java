package com.hmdp.tool;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.AiEvidenceDTO;
import com.hmdp.dto.response.ShopProfileTagDTO;
import com.hmdp.entity.Blog;
import com.hmdp.service.ShopProfileService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Component
public class ShopProfileTool {

    @Resource
    private BlogTool blogTool;

    @Resource
    private ShopProfileService shopProfileService;

    /**
     * 构建商户画像上下文
     */
    public String buildShopProfileContext(Long shopId, String shopName) {
        List<Blog> blogs = blogTool.queryBlogsByShopId(shopId, 5);
        if (CollUtil.isEmpty(blogs)) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("店铺名称：").append(shopName == null ? "" : shopName).append("\n");

        int index = 1;
        for (Blog blog : blogs) {
            if (StrUtil.isNotBlank(blog.getTitle())) {
                sb.append("探店标题").append(index).append("：").append(cleanText(blog.getTitle())).append("\n");
            }
            if (StrUtil.isNotBlank(blog.getContent())) {
                sb.append("探店内容").append(index).append("：").append(cleanText(blog.getContent())).append("\n");
            }
            index++;
        }

        return sb.toString();
    }

    /**
     * 清洗 html / 特殊换行
     */
    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("<br\\s*/?>", " ")
                .replaceAll("\\\\n", " ")
                .replaceAll("\n", " ")
                .replaceAll("\r", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public AiEvidenceDTO queryShopProfileEvidence(Long shopId, String shopName) {
        if (shopId == null) {
            return null;
        }

        ShopProfileTagDTO profile = shopProfileService.getShopProfileTag(shopId, shopName);
        if (profile == null) {
            return null;
        }

        if (profile.getSummary() == null || profile.getSummary().trim().isEmpty()) {
            return null;
        }

        AiEvidenceDTO evidence = new AiEvidenceDTO();
        evidence.setSourceType("SHOP_PROFILE");
        evidence.setSourceId(shopId);
        evidence.setTitle("商户画像摘要");
        evidence.setContent(profile.getSummary());
        return evidence;
    }
}