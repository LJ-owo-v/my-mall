package com.mymall.order.service;

import com.mymall.order.model.*;
import com.mymall.order.domain.*;

import java.util.List;

public interface OmsOrderService {
    List<OmsOrder> list(OmsOrderQueryParam queryParam, Integer pageSize, Integer pageNum);

    int delivery(List<OmsOrderDeliveryParam> deliveryParamList);

    int close(List<Long> ids, String note);

    int delete(List<Long> ids);

    OmsOrderDetail detail(Long id);

    int updateReceiverInfo(OmsReceiverInfoParam receiverInfoParam);

    int updateMoneyInfo(OmsMoneyInfoParam moneyInfoParam);

    int updateNote(Long id, String note, Integer status);
}
