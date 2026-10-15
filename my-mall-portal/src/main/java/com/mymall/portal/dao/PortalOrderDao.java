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

    void releaseSkuStockLock(@Param("itemList")List<OmsOrderItem> orderItemList);
}
