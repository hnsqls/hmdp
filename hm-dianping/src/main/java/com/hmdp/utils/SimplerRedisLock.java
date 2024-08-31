package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;


public class SimplerRedisLock implements  ILock{

    private StringRedisTemplate stringRedisTemplate;

    //业务名称
    private String name;
    //redis 中key 的前缀
    private  static  final String KEY_PREFIX ="lock:";

    private  static  final String ID_PREFIX = UUID.fastUUID().toString(true);

    public SimplerRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    /**
     * 获取锁
     * @param timeoutSec
     * @return
     */
    @Override
    public boolean tryLock(long timeoutSec) {

        //获取线程标识
        String ThreadValue = ID_PREFIX +Thread.currentThread().getId();
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, ThreadValue, timeoutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(aBoolean);
    }

    /**
     * 释放锁
     */
    @Override
    public void unlock() {

        //判断一下是不是当钱线程的锁防止别的线程删除
        //获取线程标识
        String ThreadValue = ID_PREFIX +Thread.currentThread().getId();
        //获取锁标识
        String stringId = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if (ThreadValue.equals(stringId)) {
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
