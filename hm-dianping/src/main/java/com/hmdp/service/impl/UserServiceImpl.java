package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * @author ls
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private  StringRedisTemplate redisTemplate;
    /**
     * 发送短信验证码
     * @param phone
     * @param session
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //判断手机号是否合法
        if(RegexUtils.isPhoneInvalid(phone)){
            //不合法， 就返回不符合
            return Result.fail("手机号不合法");
        }
        //合法  生成验证码
        String code = RandomUtil.randomNumbers(6);

        //保存到session中
        //session.setAttribute("code",code);

        //以手机号为k，手机验证码为v 保存在redis中
        redisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone,code,60, TimeUnit.SECONDS);

        //发送验证码到手机   sms服务，先假装发送可以记录到日志中
        log.debug("发送验证码成功,验证码:{}",code);
        return Result.ok();
    }

    /**
     * 登录或注册用户
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验验证码
//        String cacheCode = session.getAttribute("code").toString();
        String cacheCode = redisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());
        String code = loginForm.getCode();
        if (!code.equals(cacheCode)){
            //验证码不相同
            return Result.fail("验证码错误");
        }

        //根据手机号判断用户是否存在
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone,loginForm.getPhone());
        queryWrapper.select(User::getId,User::getNickName,User::getIcon);
        User user = super.getOne(queryWrapper);
        if (user ==null){
            //用户不存在，注册用户，填上基本信息,保存
            User user1 = new User();
            user1.setPhone(loginForm.getPhone());
            user1.setNickName("user_"+RandomUtil.randomString(4));
            user = user1;
            //保存到数据库中
            super.save(user1);
        }
        //用户存在就保存在session
        //session.setAttribute("user",user);

        //用户信息保存在redis中  以随机token为k,用户信息为v
        String token = UUID.randomUUID().toString(true);
        //将对象转为hash类型
        //hutool
//        Map<String, Object> usermap = BeanUtil.beanToMap(user,new HashMap<>(),
//                CopyOptions.create()
//                        .setIgnoreNullValue(true)
//                        .setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));

        //自定义函数 对象转map.
        Map<String, String> usermap = convert(user);
        //user ->> usermap0

//        Map<String, Object> usermap = BeanUtil.beanToMap(user);//这种会报错，可以转成map但是Long 类型的id,在存redis中要求变成string类型会报错。
        //存在redis中
        redisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY+token,usermap);
        //设置token有效期
        redisTemplate.expire(RedisConstants.LOGIN_USER_KEY +token,30,TimeUnit.MINUTES);

        //返回token
        return Result.ok(token) ;
    }

    /**
     * 对象 转 map
     */
    public static Map<String, String> convert(Object obj) {
        Map<String, String> result = new HashMap<>();

        // 获取对象的类
        Class<?> clazz = obj.getClass();

        // 遍历类的所有字段
        for (Field field : clazz.getDeclaredFields()) {
            // 设置字段为可访问
            field.setAccessible(true);

            try {
                // 获取字段的值
                Object value = field.get(obj);

                // 将字段名和值添加到Map中
                if (value != null) {
                    result.put(field.getName(), value.toString());
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                // 可以选择记录日志或抛出异常，这里简单处理为打印堆栈跟踪
            }
        }

        return result;
    }

}
