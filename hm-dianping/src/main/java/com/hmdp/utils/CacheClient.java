package com.hmdp.utils;

import cn.hutool.core.lang.func.Func;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import javafx.beans.binding.ObjectExpression;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * Redis 工具类
 * * 方法1：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
 * * 方法2：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
 *
 * * 方法3：根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
 * * 方法4：根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
 */
@Component
public class CacheClient {

    private  final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //range

    /**
     * 方法1：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key , Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time,unit);
    }


    /**
     * 方法2：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
     * @param key  redis的key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicalExpire(String key , Object value, Long time, TimeUnit unit){

        //RedisData 是自定义类
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    /**
     *  方法3：根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
     * @param
     * @param id
     * @return
     */
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time,TimeUnit unit){
        String key = keyPrefix+ id;

        // 1. 从redis中查询店铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否命中缓存  isnotblank false: "" or "/t/n" or "null"
        if(StrUtil.isNotBlank(json)){
            // 3.若命中则返回信息
            R r = JSONUtil.toBean(json, type);
            //            return Result.fail("没有该商户信息");
            return r;
        }
        //数据穿透判空   不是null 就是空串 ""
        if (json != null){
            return null;
        }
        //4.没有命中缓存，查数据库，因为不知道操作那个库，函数式编程，逻辑交给调用者完成
//       R r= getById(id); 交给调用者--》》函数式编程
        R r = dbFallback.apply(id);
        //5. 数据库为空，返回错误---》解决缓存穿透--》加入redis为空
        if (r == null){
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//            return Result.fail("没有该商户信息");
            return null;
        }

        //6. 数据库不为空，返回查询的结果并加入缓存
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r),time, unit);
        return r;
    }

    /**
     *  方法4：根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
     * @param id
     * @return
     */
    public <R,ID> R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R>dbFallback,String lockPrefix,Long time,TimeUnit unit){
        String key = keyPrefix+ id;

        // 1. 从redis中查询店铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.判断数据是否存在（我们对于热点key设置永不过期）  isblank
        if(StrUtil.isBlank(json)){
            // 3.若未命中中则返回空
            return null;
        }

        //4.若命中缓存 判断是否过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();

        //未过期 直接返回查询信息
        if (expireTime.isAfter(LocalDateTime.now())){
            return r;
        }
        //过期
        // 重建缓存
        // 获取锁
        String lockKey = lockPrefix + id;
        if (tryLock(lockKey)) {
            //再次校验缓存是否未过期（线程1刚写入缓存然后释放锁，线程2在线程1释放锁的同时，执行到获得锁）
            //  从redis中查询店铺缓存
            json = stringRedisTemplate.opsForValue().get(key);

            //2.判断数据是否存在（我们对于热点key设置永不过期）  isblank
            if(StrUtil.isBlank(json)){
                // 3.若未命中中则返回空
                return null;
            }

            //4.若命中缓存 判断是否过期
            redisData = JSONUtil.toBean(json, RedisData.class);
            data = (JSONObject) redisData.getData();
            r = JSONUtil.toBean(data, type);
            expireTime = redisData.getExpireTime();

            //未过期 直接返回查询信息
            if (expireTime.isAfter(LocalDateTime.now())){
                return r;
            }

            //二次校验过后还时过期的就新开线程重构缓存


            // 获得锁,开启新线程，重构缓存 ，老线程直接返回过期信息
            CACHE_REBUILD_EXECUTOR.submit( ()->{

                try{
                    //重建缓存
                    //先查数据库 封装逻辑过期时间 再写redis
                    R r1 = dbFallback.apply(id);

                    this.setWithLogicalExpire(key, r1, time, unit);


                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
            });

        }
        //未获得锁 直接返回无效信息
        return r;
    }

    /**缓存穿透互斥锁解
     *
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @return
     */
    public <R,ID>  R queryMutex(String keyPrefix, ID id, Class<R> type, Function<ID,R>dbFallback,String lockPrefix, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        //1.从redis中查询店铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断数据是否存在缓存
        if (StrUtil.isNotBlank(json)) {
            //2.1存在缓存
            R r = JSONUtil.toBean(json, type);
            return r;
        }
        //  2.2 是否缓存“”
        //判断命中是否为空值  ""
        if (json != null) {
            return null;
        }
        // 2.3不存在缓存
        // 3 缓存重建
        // 3.1 获取互斥锁
        String lockKey = lockPrefix + id;
        R r = null;
        try {
        boolean isLock = tryLock(lockKey);
        // 成功获取锁 - 》查数据库缓存重建
        if (isLock) {
            //二次校验 缓存是否有值
            json = stringRedisTemplate.opsForValue().get(key);
            //判断缓存是否存在
            if (StrUtil.isNotBlank(json)) {
                //存在缓存
                r = JSONUtil.toBean(json, type);
                return r;
            }
            if (json != null) {
                //缓存为 ""
                return null;
            }
            // 缓存不存在--》 查询数据库


            //  查询数据库
            r = dbFallback.apply(id);
            if (r == null) {
                //缓存空值
                stringRedisTemplate.opsForValue().set(key, "", time, unit);
            }
            //缓存重建
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), time, unit);
            //返回数据
            return r;
        }
        // 3.2 获取锁失败 -》休眠重试
            //休眠
            Thread.sleep(50);
            // 递归重试
            return queryMutex(keyPrefix, id, type, dbFallback, lockPrefix, time, unit);
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            unlock(lockKey);
        }
    }



    //endrange
    /**
     * 线程池
     */
    private  static  final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 获取所
     * @param key
     * @return
     */
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);

    }

    /**
     * 释放锁
     * @param key
     */
    private  void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
