package com.mymall.portal.service;

import com.mymall.common.api.CommonPage;
import com.mymall.portal.domain.ConfirmOrderResult;
import com.mymall.portal.domain.OmsOrderDetail;
import com.mymall.portal.domain.OrderParam;

import java.util.List;
import java.util.Map;

public interface OmsPortalOrderService {
    ConfirmOrderResult generateConfirmOrder(List<Long> cartIds);

    Map<String, Object> generateOrder(OrderParam orderParam);

    Integer paySuccess(Long orderId, Integer payType);

    Integer cancelTimeOutOrder();

    void sendDelayMessageCancelOrder(Long orderId);

    CommonPage<OmsOrderDetail> list(Integer status, Integer pageNum, Integer pageSize);

    OmsOrderDetail detail(Long orderId);

    void cancelOrder(Long orderId);

    void confirmReceiveOrder(Long orderId);

    void deleteOrder(Long orderId);
}
