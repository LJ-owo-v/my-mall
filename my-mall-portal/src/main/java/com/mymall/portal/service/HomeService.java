package com.mymall.portal.service;

import com.mymall.portal.domain.HomeContentResult;
import com.mymall.portal.model.CmsSubject;
import com.mymall.portal.model.PmsProduct;
import com.mymall.portal.model.PmsProductCategory;

import java.util.List;

public interface HomeService {
    HomeContentResult content();

    List<PmsProduct> recommendProductList(Integer pageSize, Integer pageNum);

    List<PmsProductCategory> getProductCateList(Long parentId);

    List<CmsSubject> getSubjectList(Long cateId, Integer pageSize, Integer pageNum);

    List<PmsProduct> hotProductList(Integer pageNum, Integer pageSize);

    List<PmsProduct> newProductList(Integer pageNum, Integer pageSize);
}
