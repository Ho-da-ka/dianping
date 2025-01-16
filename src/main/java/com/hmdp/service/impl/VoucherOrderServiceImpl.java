package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                // 获取优惠券id
                voucherId.toString(),
                // 获取用户id
                UserHolder.getUser().getId().toString()
        );
        if (result == 1L) {
            return Result.fail("库存不足");
        } else if (result == 2L) {
            return Result.fail("不能重复下单");
        }
        long orderId = redisIdWorker.nextId("order");
        // 发送消息到消息队列去创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);
        //存入消息队列等待异步消费
        rabbitTemplate.convertAndSend("seckill.direct", "seckill.order", voucherOrder);
        return Result.ok(orderId);
    }

    public void handleVoucherOrder(VoucherOrder voucherOrder) {
        //1.获取用户
        Long userId = voucherOrder.getUserId();
        //2.创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //3.获取锁
        boolean isLock = lock.tryLock();
        //4.判断是否获取锁成功
        if (!isLock) {
            log.error("不允许重复下单");
            return;
        }
        try {
            //获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            proxy.creatVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public void creatVoucherOrder(VoucherOrder voucherOrder) {
        Long voucherId = voucherOrder.getVoucherId();
        Long userId = voucherOrder.getUserId();
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            log.error("不能重复下单");
        }
        boolean result = seckillVoucherService
                .update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!result) {
            log.error("库存不足");
        }
        save(voucherOrder);
    }
}
/*
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
}*/
/*
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
}*/
