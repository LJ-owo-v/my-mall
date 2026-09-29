package com.mymall.product.service;

import com.mymall.product.domain.PmsProductAttributeParam;
import com.mymall.product.domain.ProductAttrInfo;
import com.mymall.product.model.PmsProductAttribute;

import java.util.List;

public interface PmsProductAttributeService {
    List<PmsProductAttribute> getList(Long cid, Integer type, Integer pageSize, Integer pageNum);

    int create(PmsProductAttributeParam productAttributeParam);

    int update(Long id, PmsProductAttributeParam productAttributeParam);

    PmsProductAttribute getItem(Long id);

    int delete(List<Long> ids);

    List<ProductAttrInfo> getProductAttrInfo(Long productCategoryId);
}
