package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
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
    private RedisWorker redisWorker;


    /**
     * 秒杀下单
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {

        // 1、查询优惠券信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        // 2、判断秒杀是否开始
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
            // 3、秒杀未开始，未达到开始时间，返回异常
            Result.fail("秒杀未开始，未达到开始时间！");
        }
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
            // 3、秒杀已结束，已到失效时间，返回异常
            Result.fail("秒杀已结束，已到失效时间！");
        }

        // 4、秒杀开始，判断库存是否充足
        if (seckillVoucher.getStock() <= 0){
            // 5.1、库存不充足，返回异常
            Result.fail("库存不充足！");
        }

        // 获取用户id
        Long userId = UserHolder.getUser().getId();
//        Long userId = 10L;
        // 一人一单 + 下单
        // 按用户id加锁：先获取锁，再提交事务，最后释放锁，才可以保证线程安全
        // intern 确保 userId.toString() 字符串在JVM中是全局唯一的。
        // 确保 多个线程对 同一个userId 加锁时，使用的是同一个锁对象
        synchronized (userId.toString().intern()){
            // 获取代理对象（保证事务生效）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }


    /**
     * 一人一单 + 下单
     * @param voucherId
     * @return
     */
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 8、一人一单逻辑。根据优惠券id 和 用户id 查询订单
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
//        Long userId = 10L;
        Integer countVoucherOrder = query().eq("user_id", userId).eq("voucher_id", voucherId).count();

        // 8.1、判断订单是否存在
        if (countVoucherOrder > 0){
            // 订单存在，返回异常
            return Result.fail("用户已经购买过该秒杀券，无法再次购买！");
        }

        // 用户订单不存在
        // 5.2、库存充足，扣减库存
        boolean updateSuccess = seckillVoucherService
                .update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)
                .update();
        if (!updateSuccess){
            // 库存扣减失败
            return Result.fail("库存不足，库存扣减失败！");
        }

        // 6、创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 6.1、设置订单 主键
        Long orderId = redisWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 6.2、设置订单 用户id
        voucherOrder.setUserId(userId);
        // 6.3、设置订单 代金券id
        voucherOrder.setVoucherId(voucherId);
        // 6.4、向数据库插入数据
        save(voucherOrder);

        // 7、返回订单id
        return Result.ok(orderId);
    }

}
