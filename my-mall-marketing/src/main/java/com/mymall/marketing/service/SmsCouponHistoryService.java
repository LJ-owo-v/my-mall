package com.mymall.marketing.service;

import com.mymall.marketing.model.SmsCouponHistory;

import java.util.List;

public interface SmsCouponHistoryService {
    List<SmsCouponHistory> list(Long couponId, Integer useStatus, String orderSn, Integer pageSize, Integer pageNum);
}
