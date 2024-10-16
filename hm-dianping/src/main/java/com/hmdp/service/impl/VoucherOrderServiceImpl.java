package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimplerRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author ls
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;


    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private RedisIdWorker redisIdWorker;

    /**
     * redission分布式锁使用
     */
    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 加载lua脚本
     */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 阻塞队列
     */
    private  BlockingQueue<VoucherOrder> orderTasks =new ArrayBlockingQueue<>(1024*1024);

    /**
     * 线程池
     */
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     *代理对象
     */
    private IVoucherOrderService proxy ;
    //在类初始化之后执行，因为当这个类初始化好了之后，随时都是有可能要执行的
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    /**
     *
     */
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true){
                try {
                    // 1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }

        }
    }

    /**
     *
     * @param voucherOrder
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //获取redissionClient  获取分布式错
        //userid 不能够从threadLocal中获取了因为线程变了，是子线程
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" +userId);

        //尝试获取锁
        //参数说明 第一个参数long代表等待获取锁时长默认-1 第二个参数long过期时间默认30s 第三个时间单位
        boolean isLock = lock.tryLock();

        if (!isLock) {
            //获取锁失败。返回错误信息
            log.error("不允许重复下单");
            return ;
        }
        //获取锁成功

        try {
            //获取代理对象
            //开启异步线程就不是threadLocal了，因为线程变了就拿不到代理对象了
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();

             proxy.createVoucherOrder2(voucherOrder);
        } finally {
            //释放锁
            lock.unlock();
        }
    }


    /**
     * 秒杀优惠卷下单（异步优化代码）
     * @param voucherId
     * @return
     */
    @Override
    //两表开启事务
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        LocalDateTime endTime = seckillVoucher.getEndTime();
        LocalDateTime now = LocalDateTime.now();
        //下单时间，不在优惠卷使用时间
        if (beginTime.isAfter(now)) {
            return Result.fail("秒杀还未开始");
        }
        if (endTime.isBefore(now)) {
            return Result.fail("秒杀已经结束");
        }
        // lua 脚本 判度是否能下单成功 1. 库存  2. 是否第一次下单
        //返回值 1 表示没库存 2 表示重复下单  3 可以下单
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                UserHolder.getUser().getId().toString()

        );
        // Long - 》 int
        int r = result.intValue();
        if (r == 1){
            return Result.fail("库存不足");
        }
        if (r == 2){
            return Result.fail("重复下单");
        }
        //有购买资格，把下单信息保存在阻塞队列 订单id 用户id 优惠卷id
        long orderId = redisIdWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);;
        // todo 保存到阻塞队列
        orderTasks.add(voucherOrder);

        //获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 异步线程处理
        // 返回订单id
        return Result.ok(orderId);


    }



    /**
     * 秒杀优惠卷下单(old)
     *
     * @param voucherId
     * @return
     */
//    @Override
//    //两表开启事务
//    public Result seckillVoucher(Long voucherId) {
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        LocalDateTime beginTime = seckillVoucher.getBeginTime();
//        LocalDateTime endTime = seckillVoucher.getEndTime();
//        LocalDateTime now = LocalDateTime.now();
//        //下单时间，不在优惠卷使用时间
//        if (beginTime.isAfter(now)) {
//            return Result.fail("秒杀还未开始");
//        }
//        if (endTime.isBefore(now)) {
//            return Result.fail("秒杀已经结束");
//        }
////        判断库存是否充足---》
//        int stock = seckillVoucher.getStock();
//
//        if (stock <= 0) {
//            return Result.fail("库存不足");
//        }
//

//
//
//
//        /**下单库存减一 解决超卖问题使用乐观锁,
//         * 但是以上这种方式通过测试发现会有很多失败的情况，
//         * 失败的原因在于：在使用乐观锁过程中假设100个线程同时都拿到了100的库存，
//         * 然后大家一起去进行扣减，但是100个人中只有1个人能扣减成功，
//         * 其他的人在处理时，他们在扣减时，库存已经被修改过了，
//         * 所以此时其他线程都会失败.
//         */
////        boolean success = seckillVoucherService.update().
////                setSql("stock = stock - 1")
////                .eq("voucher_id", voucherId)
////                .eq("stock",stock)
////                .update();
//
//
//        /**
//         * sync锁
//         *
//         */
//
////        synchronized (UserHolder.getUser().getId().toString().intern()){
////            //解决事务不生效问题原因就是下面的方法是this.而不是sprig代理的方法
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
//
//
//        //分布式锁 redis 自己实现 锁定范围下单的用户id
//
//        // range
//        //创建工具
////         SimplerRedisLock lock = new SimplerRedisLock(new StringRedisTemplate(), "order:"+UserHolder.getUser().getId());
//        //尝试获取锁
////        boolean isLock = lock.tryLock(5);//自己定义的setnx
//        // ranged
//
//        /**
//         * redission分布式锁
//         */
//
//        //获取redissionClient  获取分布式错
//        RLock lock = redissonClient.getLock("lock:order:" + UserHolder.getUser().getId());
//
//        //尝试获取锁
//        //参数说明 第一个参数long代表等待获取锁时长默认-1 第二个参数long过期时间默认30s 第三个时间单位
//        boolean isLock = lock.tryLock();
//
//        if (!isLock) {
//            //获取锁失败。返回错误信息
//            return Result.fail("只能下一单");
//
//        }
//        //获取锁成功
//
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            //释放锁
//            lock.unlock();
//        }
//
//    }


    /**
     * 创建订单 old
     * @param voucherId
     * @return
     */
    @Transactional
    public  Result createVoucherOrder(Long voucherId) {
//        synchronized (UserHolder.getUser().getId().toString().intern()) { 此处加锁，是先释放锁在提交事务，假如还未提交又有新的进程进来就有问题

            //todo : 一人一单的判断
            int count = this.query()
                    .eq("user_id", UserHolder.getUser().getId())
                    .eq("voucher_id", voucherId)
                    .count();

            if (count > 0) {
                return Result.fail("用户已经达到购买上限");
            }


            //更新数据库 下单

            /**
             * 乐观锁的改造
             */
            boolean success = seckillVoucherService.update().
                    setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();
            if (!success) {
                return Result.fail("库存不足");
            }
            //创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            long nextId = redisIdWorker.nextId("order");

            voucherOrder.setId(nextId);
            voucherOrder.setUserId(UserHolder.getUser().getId());
            voucherOrder.setVoucherId(voucherId);

            voucherOrderService.save(voucherOrder);

            return Result.ok(nextId);
        }


    /**
     * 创建订单  使用异步队列改造后的业务逻辑
     * @param voucherOrder
     */
    @Override
    @Transactional
    public void createVoucherOrder2(VoucherOrder voucherOrder) {

        Long userId = voucherOrder.getUserId();
        //todo : 一人一单的判断
        int count = this.query()
                .eq("user_id", UserHolder.getUser().getId())
                .eq("voucher_id", userId)
                .count();

        if (count > 0) {
            log.error("不能重复购买");
            return ;
        }

        boolean success = seckillVoucherService.update().
                setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足");
            return ;
        }

        save(voucherOrder);
    }
//    }

}
