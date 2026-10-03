package com.mymall.marketing.service;

import com.mymall.marketing.model.SmsHomeBrand;

import java.util.List;

public interface SmsHomeBrandService {
    int create(List<SmsHomeBrand> homeBrandList);

    int updateSort(Long id, Integer sort);

    int delete(List<Long> ids);

    int updateRecommendStatus(List<Long> ids, Integer recommendStatus);

    List<SmsHomeBrand> list(String brandName, Integer recommendStatus, Integer pageSize, Integer pageNum);
}
