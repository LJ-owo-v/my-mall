package com.mymall.marketing.service;

import com.mymall.marketing.model.SmsHomeNewProduct;

import java.util.List;

public interface SmsHomeNewProductService {
    int create(List<SmsHomeNewProduct> homeBrandList);

    int updateSort(Long id, Integer sort);

    int delete(List<Long> ids);

    int updateRecommendStatus(List<Long> ids, Integer recommendStatus);

    List<SmsHomeNewProduct> list(String productName, Integer recommendStatus, Integer pageSize, Integer pageNum);
}
