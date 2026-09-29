package com.mymall.product.service;

import com.mymall.product.domain.PmsProductCategoryParam;
import com.mymall.product.domain.PmsProductCategoryWithChildrenItem;
import com.mymall.product.model.PmsProductCategory;

import java.util.List;

public interface PmsProductCategoryService {
    int create(PmsProductCategoryParam productCategoryParam);

    int update(Long id, PmsProductCategoryParam productCategoryParam);

    List<PmsProductCategory> getList(Long parentId, Integer pageSize, Integer pageNum);

    PmsProductCategory getItem(Long id);

    int delete(Long id);

    int updateNavStatus(List<Long> ids, Integer navStatus);

    int updateShowStatus(List<Long> ids, Integer showStatus);

    List<PmsProductCategoryWithChildrenItem> listWithChildren();
}
