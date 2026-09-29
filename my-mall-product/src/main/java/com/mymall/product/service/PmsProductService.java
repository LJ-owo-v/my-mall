package com.mymall.product.service;

import com.mymall.product.domain.PmsProductParam;
import com.mymall.product.domain.PmsProductQueryParam;
import com.mymall.product.domain.PmsProductResult;
import com.mymall.product.model.PmsProduct;

import java.util.List;

public interface PmsProductService {
    int create(PmsProductParam productParam);

    PmsProductResult getUpdateInfo(Long id);

    int update(Long id, PmsProductParam productParam);

    List<PmsProduct> list(PmsProductQueryParam productQueryParam, Integer pageSize, Integer pageNum);

    List<PmsProduct> list(String keyword);

    int updateVerifyStatus(List<Long> ids, Integer verifyStatus, String detail);

    int updatePublishStatus(List<Long> ids, Integer publishStatus);

    int updateRecommendStatus(List<Long> ids, Integer recommendStatus);

    int updateNewStatus(List<Long> ids, Integer newStatus);

    int updateDeleteStatus(List<Long> ids, Integer deleteStatus);
}
