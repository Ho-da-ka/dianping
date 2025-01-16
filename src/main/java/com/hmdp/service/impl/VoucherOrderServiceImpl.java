package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher == null) {
            return Result.fail("秒杀券不存在");
        }
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀券还没开始");
        }
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀券已经结束");
        }
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        RLock lock = redissonClient.getLock("lock:order:" + UserHolder.getUser().getId());
        if (!lock.tryLock()) {
            return Result.fail("不能重复下单");
        }
        try {
            //获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.creatVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public Result creatVoucherOrder(Long voucherId) {
        Integer count = query().eq("user_id", UserHolder.getUser().getId()).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("不能重复下单");
        }
        boolean result = seckillVoucherService
                .update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!result) {
            return Result.fail("库存不足");
        }
        VoucherOrder order = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        order.setUserId(UserHolder.getUser().getId());
        order.setVoucherId(voucherId);
        order.setId(orderId);
        order.setCreateTime(LocalDateTime.now());
        save(order);
        return Result.ok(orderId);
    }
}
