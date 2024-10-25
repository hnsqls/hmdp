package com.hmdp.config;

import com.hmdp.custom.interceptor.LoginInterceptor;
import com.hmdp.custom.interceptor.RefleshTokenInterceptor;
import com.hmdp.custom.interceptor.RefleshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    //登录拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/shop-type/**",
                        "/shop/**",
                        "/blog/hot"
                ).order(1);

        //刷新token拦截器
        registry.addInterceptor(new RefleshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0);
    }
}
