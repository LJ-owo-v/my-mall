package com.mymall.product.dao;

import com.mymall.product.domain.PmsProductAttributeCategoryItem;
import com.mymall.product.model.PmsProductAttributeCategory;

import java.util.List;

public interface PmsProductAttributeCategoryDao {
    List<PmsProductAttributeCategoryItem> getListWithAttr();

    ;
}
