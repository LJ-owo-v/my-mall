package com.mymall.portal.domain;

import com.mymall.portal.model.OmsOrder;
import com.mymall.portal.model.OmsOrderItem;

import java.util.List;

/**
 * 包含订单商品信息的订单详情
 */
public class OmsOrderDetail extends OmsOrder {
    private List<OmsOrderItem> orderItemList;

    public List<OmsOrderItem> getOrderItemList() {
        return orderItemList;
    }

    public void setOrderItemList(List<OmsOrderItem> orderItemList) {
        this.orderItemList = orderItemList;
    }
}
