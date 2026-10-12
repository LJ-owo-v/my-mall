package com.mymall.portal.service;

import com.mymall.common.api.CommonPage;
import com.mymall.portal.model.PmsBrand;
import com.mymall.portal.model.PmsProduct;

import java.util.List;

public interface PortalBrandService {
    List<PmsBrand> recommendList(Integer pageNum, Integer pageSize);

    PmsBrand detail(Long brandId);

    CommonPage<PmsProduct> productList(Long brandId, Integer pageNum, Integer pageSize);
}
