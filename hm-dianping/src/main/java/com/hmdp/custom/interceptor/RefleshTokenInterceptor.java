package com.hmdp.custom.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.entity.User;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * 刷新token拦截器，拦截所有
 *
 * */
public class RefleshTokenInterceptor implements HandlerInterceptor {


    private StringRedisTemplate stringRedisTemplate;

    public RefleshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //  1. 获取token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            //为空 放行
//            response.setStatus(401);
            return  true;
        }

        // 2.基于token获取redis中的用户
        Map<Object, Object> objectMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY+token);
        //判断用户是否存在
        if (objectMap.isEmpty()){
            //不存在 放行
//            response.setStatus(401);
            return true;
        }
        //将map转对象
        User user = BeanUtil.fillBeanWithMap(objectMap, new User(), false);

        //保存再threadLocal
        UserHolder.saveUser(user);

        //刷新token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        return true;

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
       //移除信息，避免内存泄露
        UserHolder.removeUser();
    }
}
