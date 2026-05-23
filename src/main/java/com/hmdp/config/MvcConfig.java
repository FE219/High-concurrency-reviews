package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;


@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // LoginInterceptor: only blocks paths that require login
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns(
                        "/voucher-order/**",
                        "/user/sign",
                        "/blog/like/**",
                        "/blog/save",
                        "/follow/**",
                        "/ai/chat"
                )
                .order(1);

        // RefreshTokenInterceptor: runs on ALL paths (optional login)
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0);
    }
}
