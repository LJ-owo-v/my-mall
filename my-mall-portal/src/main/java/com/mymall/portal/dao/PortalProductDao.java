package com.mymall.portal.dao;

import com.mymall.portal.domain.CartProduct;
import com.mymall.portal.domain.PromotionProduct;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PortalProductDao {
    CartProduct getCartProduct(@Param("id")Long id);

    List<PromotionProduct> getPromotionProductList(@Param("ids")List<Long> productIdList);
}
