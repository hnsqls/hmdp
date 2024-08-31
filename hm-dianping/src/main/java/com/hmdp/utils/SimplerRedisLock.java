package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;


public class SimplerRedisLock implements ILock {

    private StringRedisTemplate stringRedisTemplate;

    //业务名称
    private String name;
    //redis 中key 的前缀
    private static final String KEY_PREFIX = "lock:";

    //确保分布式系统下id不同
    private static final String ID_PREFIX = UUID.fastUUID().toString(true);

    // lua 脚本

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    public SimplerRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    /**
     * 获取锁
     *
     * @param timeoutSec
     * @return
     */
    @Override
    public boolean tryLock(long timeoutSec) {

        //获取线程标识
        String ThreadValue = ID_PREFIX + Thread.currentThread().getId();
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, ThreadValue, timeoutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(aBoolean);
    }


    /**
     * 释放锁 可能会有判断所和删除不是原子操作
     * 导致数据安全问题
     * 优化就是 lua脚本  下面
     */
//    @Override
//    public void unlock() {
//
//        //判断一下是不是当钱线程的锁防止别的线程删除
//        //获取线程标识
//        String ThreadValue = ID_PREFIX +Thread.currentThread().getId();
//        //获取锁标识
//        String stringId = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        if (ThreadValue.equals(stringId)) {
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }


    /**
     * 释放锁
     * 确保判断和释放是原子操作
     * lua脚本
     */
    @Override
    public void unlock() {
        //调用lua脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name), ID_PREFIX + Thread.currentThread().getId());
    }


}
