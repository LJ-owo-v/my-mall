package com.mymall.product.dao;

import com.mymall.product.domain.PmsProductCategoryWithChildrenItem;

import java.util.List;

public interface PmsProductCategoryDao {
    List<PmsProductCategoryWithChildrenItem> listWithChildren();
}
