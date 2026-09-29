package com.mymall.product.dao;

import com.mymall.product.domain.ProductAttrInfo;

import java.util.List;

public interface PmsProductAttributeDao {
    List<ProductAttrInfo> getProductAttrInfo(Long productCategoryId);
}
