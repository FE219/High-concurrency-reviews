package com.hmdp.tool;

import com.hmdp.dto.AiEvidenceDTO;
import com.hmdp.dto.tool.ShopSimpleDTO;
import com.hmdp.dto.tool.VoucherSimpleDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AiToolDefinitions {

    private final ShopTool shopTool;
    private final CouponTool couponTool;
    private final RuleTool ruleTool;
    private final ShopProfileTool shopProfileTool;
    private final BlogTool blogTool;
    private final ShopTypeTool shopTypeTool;
    private final UserTool userTool;

    /**
     * Search shops by keyword. Use when user asks to find stores by name or category.
     */
    public List<ShopSimpleDTO> searchShops(String keyword, Double lat, Double lon) {
        return shopTool.searchByKeyword(keyword, 1);
    }

    /**
     * Query available coupons/vouchers for a specific shop.
     */
    public List<VoucherSimpleDTO> queryCoupons(Long shopId) {
        return couponTool.queryShopCoupons(shopId);
    }

    /**
     * Get detailed information about a specific shop.
     */
    public ShopSimpleDTO getShopDetail(Long shopId) {
        return shopTool.getShopById(shopId);
    }

    /**
     * Query platform rules and policies documentation.
     */
    public List<AiEvidenceDTO> queryRuleEvidence(String question) {
        return ruleTool.queryRuleEvidence(question);
    }

    /**
     * Query shop reviews, atmosphere, and user experience feedback.
     */
    public List<AiEvidenceDTO> queryShopProfile(Long shopId, String question) {
        return blogTool.queryShopBlogEvidence(shopId, question, 3);
    }

    /**
     * Get all available shop type categories.
     */
    public List<String> queryShopTypes() {
        return shopTypeTool.getAllTypes();
    }

    /**
     * Get current user's preference profile for personalized recommendations.
     */
    public String queryUserPreferences(Long userId) {
        return userTool.getPreferences(userId);
    }

    /**
     * Query current user's order history.
     */
    public String queryUserOrders(Long userId) {
        return userTool.getOrders(userId);
    }
}
