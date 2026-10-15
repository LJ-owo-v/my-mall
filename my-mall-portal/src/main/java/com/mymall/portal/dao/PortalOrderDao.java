package com.mymall.portal.dao;

import com.mymall.portal.domain.OmsOrderDetail;
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
}
