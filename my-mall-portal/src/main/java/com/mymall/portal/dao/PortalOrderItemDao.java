package com.mymall.portal.dao;

import com.mymall.portal.model.OmsOrderItem;

import java.util.List;

public interface PortalOrderItemDao {
    void insertList(List<OmsOrderItem> orderItemList);
}
