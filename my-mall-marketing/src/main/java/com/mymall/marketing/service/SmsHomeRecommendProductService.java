package com.mymall.marketing.service;

import com.mymall.marketing.model.SmsHomeRecommendProduct;

import java.util.List;

public interface SmsHomeRecommendProductService {
    int create(List<SmsHomeRecommendProduct> homeBrandList);

    int updateSort(Long id, Integer sort);

    int delete(List<Long> ids);

    int updateRecommendStatus(List<Long> ids, Integer recommendStatus);

    List<SmsHomeRecommendProduct> list(String productName, Integer recommendStatus, Integer pageSize, Integer pageNum);
}
