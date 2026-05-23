package com.hmdp.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserTool {

    /**
     * Get current user's preference profile for personalized recommendations.
     */
    public String getPreferences(Long userId) {
        log.info("getPreferences: userId={} - not yet implemented", userId);
        return "用户偏好功能暂未实现";
    }

    /**
     * Query current user's order history.
     */
    public String getOrders(Long userId) {
        log.info("getOrders: userId={} - not yet implemented", userId);
        return "用户订单查询功能暂未实现";
    }
}
