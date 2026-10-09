package com.mymall.portal.dao;

import com.mymall.portal.domain.OmsOrderDetail;
import com.mymall.portal.model.OmsOrderItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PortalOrderDao {

    OmsOrderDetail getDetail(@Param("orderId") Long orderId);

    int updateSkuStock(@Param("itemList")List<OmsOrderItem> orderItemList);

    List<OmsOrderDetail> getTimeOutOrders(@Param("minute")Integer minute);

    int updateOrderStatus(@Param("ids") List<Long> ids,@Param("status") Integer status);

    void releaseSkuStockLock(@Param("itemList")List<OmsOrderItem> orderItemList);
}
