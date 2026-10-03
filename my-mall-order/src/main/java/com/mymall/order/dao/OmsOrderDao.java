package com.mymall.order.dao;

import com.mymall.order.model.OmsOrder;
import com.mymall.order.domain.OmsOrderDeliveryParam;
import com.mymall.order.domain.OmsOrderDetail;
import com.mymall.order.domain.OmsOrderQueryParam;

import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface OmsOrderDao {
    List<OmsOrder> getList(@Param("queryParam") OmsOrderQueryParam queryParam);

    int delivery(List<OmsOrderDeliveryParam> deliveryParamList);

    OmsOrderDetail getDetail(Long id);
}
