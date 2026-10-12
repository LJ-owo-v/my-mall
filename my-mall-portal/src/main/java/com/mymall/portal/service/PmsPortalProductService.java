package com.mymall.portal.service;

import com.mymall.portal.domain.PmsPortalProductDetail;
import com.mymall.portal.domain.PmsProductCategoryNode;
import com.mymall.portal.model.PmsProduct;

import java.util.List;

public interface PmsPortalProductService {
    List<PmsProduct> search(String keyword, Long brandId, Long productCategoryId, Integer pageNum, Integer pageSize, Integer sort);

    List<PmsProductCategoryNode> categoryTreeList();

    PmsPortalProductDetail detail(Long id);
}
