package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * @author ls
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;


    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private RedisIdWorker redisIdWorker;


    /**
     * 秒杀优惠卷下单
     *
     * @param voucherId
     * @return
     */
    @Override
    //两表开启事务
    @Transactional
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
        //判断库存是否充足---》
        int stock = seckillVoucher.getStock();

        if (stock <= 0) {
            return Result.fail("库存不足");
        }
        /**下单库存减一 解决超卖问题使用乐观锁,
         * 但是以上这种方式通过测试发现会有很多失败的情况，
         * 失败的原因在于：在使用乐观锁过程中假设100个线程同时都拿到了100的库存，
         * 然后大家一起去进行扣减，但是100个人中只有1个人能扣减成功，
         * 其他的人在处理时，他们在扣减时，库存已经被修改过了，
         * 所以此时其他线程都会失败.
         */
//        boolean success = seckillVoucherService.update().
//                setSql("stock = stock - 1")
//                .eq("voucher_id", voucherId)
//                .eq("stock",stock)
//                .update();


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
}
