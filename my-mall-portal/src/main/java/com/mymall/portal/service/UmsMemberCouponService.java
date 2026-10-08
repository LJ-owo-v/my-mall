package com.mymall.portal.service;

import com.mymall.portal.domain.CartPromotionItem;
import com.mymall.portal.domain.SmsCouponHistoryDetail;
import com.mymall.portal.model.SmsCoupon;
import com.mymall.portal.model.SmsCouponHistory;

import java.util.List;

public interface UmsMemberCouponService {
    void add(Long couponId);

    List<SmsCouponHistory> listHistory(Integer useStatus);

    List<SmsCouponHistoryDetail> listCart(List<CartPromotionItem> cartPromotionItemList, Integer type);

    List<SmsCoupon> listByProduct(Long productId);

    List<SmsCoupon> list(Integer useStatus);
}
