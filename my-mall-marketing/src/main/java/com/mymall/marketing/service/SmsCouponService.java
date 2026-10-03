package com.mymall.marketing.service;

import com.mymall.marketing.domain.SmsCouponParam;
import com.mymall.marketing.model.SmsCoupon;

import java.util.List;

public interface SmsCouponService {
    int create(SmsCouponParam couponParam);

    int delete(Long id);

    int update(Long id, SmsCouponParam couponParam);

    List<SmsCoupon> list(String name, Integer type, Integer pageSize, Integer pageNum);

    SmsCouponParam getItem(Long id);
}
