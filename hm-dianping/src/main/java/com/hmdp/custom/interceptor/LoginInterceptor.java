package com.hmdp.custom.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

//@Component  拦截器是非常轻量级的组件，只有再需要时才会被调用
public class LoginInterceptor implements HandlerInterceptor {


    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        //1. 获取session
//        HttpSession session = request.getSession();
//        // 2. 获取session中的用户
//        Object user = session.getAttribute("user");
//        if (user == null) {
//            //没有用户信息
//            response.setStatus(401);
//            return false;
//        }
//        //3. 保存到ThreadLocal中
//        UserHolder.saveUser((User) user);
//
//        return true;

        // TODO 1. 获取token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            //为空
            response.setStatus(401);
            return  false;
        }

        // TODO 2.基于token获取redis中的用户
        Map<Object, Object> objectMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY+token);
        //判断用户是否存在
        if (objectMap.isEmpty()){
            //不存在
            response.setStatus(401);
            return false;
        }
        //将map转对象
        User user = BeanUtil.fillBeanWithMap(objectMap, new User(), false);

        //保存再threadLocal
        UserHolder.saveUser(user);

        //刷新token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token,RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

        return true;

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
       //移除信息，避免内存泄露
        UserHolder.removeUser();
    }
}
