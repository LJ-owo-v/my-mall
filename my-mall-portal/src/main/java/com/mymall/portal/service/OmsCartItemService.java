package com.mymall.portal.service;

import com.mymall.portal.domain.CartProduct;

import com.mymall.portal.domain.CartPromotionItem;
import com.mymall.portal.model.OmsCartItem;

import java.util.List;

public interface OmsCartItemService {
    int add(OmsCartItem cartItem);

    List<OmsCartItem> list(Long id);

    List<CartPromotionItem> listPromotion(Long id, List<Long> cartIds);

    int updateQuantity(Long id, Long id1, Integer quantity);

    CartProduct getCartProduct(Long productId);

    int updateAttr(OmsCartItem cartItem);

    int delete(Long id, List<Long> ids);

    int clear(Long id);
}
