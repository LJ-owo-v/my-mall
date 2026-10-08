package com.mymall.portal.service;

import com.mymall.portal.domain.CartPromotionItem;
import com.mymall.portal.model.OmsCartItem;

import java.util.List;

public interface OmsPromotionService {
    List<CartPromotionItem> calcCartPromotion(List<OmsCartItem> cartItemList);
}
