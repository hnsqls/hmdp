package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
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
        session.setAttribute("code",code);

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
        String cacheCode = session.getAttribute("code").toString();
        String code = loginForm.getCode();
        if (!code.equals(cacheCode)){
            //验证码不相同
            return Result.fail("验证码错误");
        }

        //根据手机号判断用户是否存在
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone,loginForm.getPhone());
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
        session.setAttribute("user",user);

        return Result.ok() ;
    }

}
