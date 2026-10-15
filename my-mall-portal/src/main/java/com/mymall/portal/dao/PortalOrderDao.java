package com.mymall.portal.dao;

import com.mymall.portal.domain.OmsOrderDetail;
import com.mymall.portal.domain.ReconcileResult;
import com.mymall.portal.model.OmsOrderItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PortalOrderDao {

    OmsOrderDetail getDetail(@Param("orderId") Long orderId);

    int updateSkuStock(@Param("itemList")List<OmsOrderItem> orderItemList);

    /**
     * 原子锁定库存（防止"查-改-写"丢失更新导致超卖）。
     * 影响行数=0 表示可售不足（stock - lock_stock &lt; quantity），调用方据此判定失败。
     *
     * @param skuId    商品 SKU id
     * @param quantity 本次要锁定的数量
     * @return 受影响行数：1=锁定成功，0=可售不足
     */
    int lockStockSafely(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    List<OmsOrderDetail> getTimeOutOrders(@Param("minute")Integer minute);

    int updateOrderStatus(@Param("ids") List<Long> ids,@Param("status") Integer status);

    /**
     * 【已由 {@link #releaseSkuStockLockSafely} 取代，保留仅为兼容】
     * 批量释放锁定库存（CASE WHEN 一次更新多个 SKU）—— <b>无下界保护</b>，
     * 重复调用会把 lock_stock 减成负数（#15-A 的成因，实测库中出现过 -24/-8 等脏值）。
     * 新代码请改用 releaseSkuStockLockSafely。
     */
    void releaseSkuStockLock(@Param("itemList")List<OmsOrderItem> orderItemList);

    /**
     * 原子抢占购物车行（下单幂等）：把指定会员的购物车行标记为已删除。
     * 只有 delete_status=0 的行会被更新 —— 并发下只有一个请求能抢到。
     *
     * @return 受影响行数；小于 cartIds.size() 说明有行已被其他请求消费 → 应拒绝下单
     */
    int occupyCartItems(@Param("memberId") Long memberId, @Param("ids") List<Long> ids);

    /**
     * 释放单个 SKU 的锁定库存（<b>带下界保护</b>，修复 #15-A）。
     * WHERE 条件带 lock_stock &gt;= quantity：锁定值不足时不动它，避免出现负数。
     *
     * @param skuId    商品 SKU id
     * @param quantity 本次要释放的数量
     * @return 受影响行数：1=释放成功；0=锁定值不足（说明已被释放过，重复取消）
     */
    int releaseSkuStockLockSafely(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    /**
     * 原子状态裁决（消除"先查后写"的 TOCTOU，修复 #15-A）：
     * 仅当订单当前是待付款(status=0) 时才改为目标状态。
     *
     * @return 受影响行数：1=本次调用成功关闭了它（调用方据此决定是否释放库存/券/积分）；
     *                     0=已被其他路径（MQ 消费者 / 定时任务 / 并发请求）处理过 → 不得重复释放
     */
    int updateOrderStatusByIdIfPending(@Param("orderId") Long orderId, @Param("status") Integer status);

    /**
     * 支付幂等（修复 #16）：仅当订单当前是待付款(0) 时才置为已支付(1)，
     * 并在同一条语句里写入支付时间与支付方式。
     * <p>
     * 支付宝会<b>重复投递</b>同一回调（返回 failure 或响应超时即按策略重试，最长 24 小时），
     * {@code query} 兜底也会重复调用；原实现无条件改单 + 无条件扣库存，
     * 导致重复回调反复扣减 stock、并把 lock_stock 扣成负数。
     *
     * @return 受影响行数：1=本次成功标记为已支付（调用方据此扣库存）；
     *                     0=订单已支付/已关闭 → <b>不得重复扣减</b>
     */
    int payOrderIfPending(@Param("orderId") Long orderId, @Param("payType") Integer payType);

    /**
     * 支付成功：真实扣减库存并释放锁定（<b>带下界保护</b>，修复 #16 配套）。
     * WHERE 同时守卫 lock_stock 与 stock：任一不足时不动它，避免扣成负数。
     *
     * @param skuId    商品 SKU id
     * @param quantity 本次扣减数量
     * @return 受影响行数：1=扣减成功；0=锁定或库存不足（说明已被处理过）
     */
    int paySkuStockSafely(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    // ================================================================
    // 对账查询（M5 #12）—— 全部【只读】，不修改任何数据
    //
    // 目的：把"我怎么知道数据是对的"从【假设】变成【可验证】。
    // 每条方法返回该不变量的【差异明细】（违反不变量的一行一条），
    // 调用方用 list.size() 判定 PASS/FAIL —— 空列表 = 通过。
    // ================================================================

    /**
     * I1 库存守恒：lock_stock 应等于该 SKU 在【待付款】订单中的数量之和。
     * 直接验证 #9(丢失更新) / #15A(重复释放) / #16(重复扣减) 三项修复的成果。
     *
     * @return 违反该不变量的 SKU 明细（空 = PASS）
     */
    List<ReconcileResult> reconcileStockConservation();

    /**
     * I2 无负值：stock &gt;= 0 且 lock_stock &gt;= 0。
     * 库里曾实存 -24 / -8 / -8 三个负 lock_stock，此条是守卫的兜底验证。
     *
     * @return 负值明细（空 = PASS）
     */
    List<ReconcileResult> reconcileNegativeStock();

    /**
     * I3 锁定不超总量：lock_stock &lt;= stock（超过即已超卖）。
     *
     * @return 超卖明细（空 = PASS）
     */
    List<ReconcileResult> reconcileLockOverStock();

    /**
     * I4 支付状态与时间一致：
     * status IN (1,2,3)（待发货/已发货/已完成）必须有 payment_time；
     * status = 0（待付款）不应有 payment_time。
     * 注意：status = 4（已关闭）本就没有支付时间，不参与判定。
     *
     * @return 不一致明细（空 = PASS）
     */
    List<ReconcileResult> reconcilePayStatusTime();

    /**
     * I5 孤儿锁定：lock_stock &gt; 0 但该 SKU 无任何【待付款】订单。
     * 这种锁定【没有任何代码路径会释放】—— 库存被永久占用（如 SKU 201）。
     *
     * @return 孤儿锁定明细（空 = PASS）
     */
    List<ReconcileResult> reconcileOrphanLock();
}
