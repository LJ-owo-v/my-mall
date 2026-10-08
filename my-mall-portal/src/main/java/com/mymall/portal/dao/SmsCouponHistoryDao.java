package com.mymall.portal.dao;

import com.mymall.portal.domain.SmsCouponHistoryDetail;
import com.mymall.portal.model.SmsCoupon;

import java.util.List;

public interface SmsCouponHistoryDao {
    List<SmsCouponHistoryDetail> getDetailList(Long id);

    List<SmsCoupon> getCouponList(Long id, Integer useStatus);
}
