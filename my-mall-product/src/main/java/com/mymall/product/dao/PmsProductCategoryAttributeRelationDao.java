package com.mymall.product.dao;

import com.mymall.product.model.PmsProductCategoryAttributeRelation;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface PmsProductCategoryAttributeRelationDao {
    int insertList(@Param("list") List<PmsProductCategoryAttributeRelation> productCategoryAttributeRelationList);
}
