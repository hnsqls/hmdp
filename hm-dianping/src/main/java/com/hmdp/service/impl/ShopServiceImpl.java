package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import lombok.SneakyThrows;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @author ls
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //range

    /**
     * 查询商户信息
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        // 缓存穿透
//        Shop shop = queryWithPassThrough(id);

        //互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);
//        if (shop ==null){
//            return Result.fail("店铺不存在");
//        }


        //逻辑过期时间解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);



        return Result.ok(shop);
    }

    /**
     * 更新商户信息
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null){
            return Result.fail("商户id不存在");
        }
        //1. 更新数据库
        this.updateById(shop);
        //2. 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+ id);
        return null;
    }

    //endrange

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

    /**
     * 查询商户信息 缓存穿透解决
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id){
        String shopKey = CACHE_SHOP_KEY+ id;

        // 1. 从redis中查询店铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);

        //2.判断是否命中缓存  isnotblank false: "" or "/t/n" or "null"
        if(StrUtil.isNotBlank(shopJson)){
            // 3.若命中则返回信息
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //数据穿透判空   不是null 就是空串 ""
        if (shopJson != null){
            //返回错误信息
//            return  Result.fail("没有该商户信息（缓存）");
            return null;
        }
        //4.没有命中缓存，查数据库
        //todo :解决缓存击穿  不能直接查数据库。 利用互斥锁解决
        Shop shop = super.getById(id);
        //5. 数据库为空，返回错误---》解决缓存穿透--》加入redis为空
        if (shop == null){
            stringRedisTemplate.opsForValue().set(shopKey,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//            return Result.fail("没有该商户信息");
            return null;
        }

        //6. 数据库不为空，返回查询的结果并加入缓存
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+ id, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    /**
     * 查询商户信息 缓存击穿互斥锁
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id){
        String shopKey = CACHE_SHOP_KEY+ id;

        // 1. 从redis中查询店铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);

        //2.判断是否命中缓存  isnotblank false: "" or "/t/n" or "null"
        if(StrUtil.isNotBlank(shopJson)){
            // 3.若命中则返回信息
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //数据穿透判空   不是null 就是空串 ""
        if (shopJson != null){
            //返回错误信息
//            return  Result.fail("没有该商户信息（缓存）");
            return null;
        }
        //4.没有命中缓存，查数据库
        //todo :解决缓存击穿  不能直接查数据库。 利用互斥锁解决

        /**
         * 实现缓存重建
         * 1. 获取互斥锁
         * 2. 判断是否成功
         * 3. 失败就休眠重试
         * 4.成功 查数据库
         * 5 数据库存在该数据写入缓存
         * 6 不存在返回错误信息并写入缓存“”
         * 7 释放锁
         *
         */

        //获取互斥锁 失败  休眠重试
        String lockKey = "lock:shop" + id;
        Shop shop = null;
        try {
            if (!tryLock(lockKey)) {

//                Thread.sleep(50);
                return queryWithMutex(id);//递归 重试
            }

            //获得锁
            // 查数据库
            shop = super.getById(id);
            Thread.sleep(200);//模拟你测试环境 热点key失效模拟重建延迟
            if (shop == null){
                stringRedisTemplate.opsForValue().set(shopKey,"",CACHE_NULL_TTL,TimeUnit.SECONDS);
                //            return Result.fail("没有该商户信息");
                return null;
            }
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+ id, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(shopKey);
        }
        return shop;

    }

    /**
     * 查询商户信息 缓存击穿逻辑过期时间
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id){
        String shopKey = CACHE_SHOP_KEY+ id;

        // 1. 从redis中查询店铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);

        //2.判断是否命中缓存  isnotblank false: "" or "/t/n" or "null"
        if(StrUtil.isBlank(shopJson)){
            // 3.若未命中中则返回空
          return null;
        }

        //4.若命中缓存 判断是否过期

        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        //未过期 直接返回店铺信息
        if (expireTime.isAfter(LocalDateTime.now())){
            return shop;

        }
        //过期
        // 重建缓存
        // 获取锁
        String lockKey = LOCK_SHOP_KEY + id;
        if (tryLock(lockKey)) {
            // 获得锁,开启新线程，重构缓存 ，老线程直接返回过期信息
            CACHE_REBUILD_EXECUTOR.submit( ()->{

                try{
                    //重建缓存
                    saveShop2Redis(id,20L);

                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
            });

        }


        //未获得锁 直接返回无效信息
        return shop;

    }


    /**
     * 添加逻辑过期时间
     * @param id
     * @param expireSeconds
     */
    public void saveShop2Redis(Long id ,Long expireSeconds){

        // 查询店铺数据
        Shop shop = getById(id);

        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        redisData.setData(shop);
        //写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }
    /**
     * 线程池
     */
    private  static  final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

}
