package com.mymall.product.service;

import com.mymall.product.domain.PmsBrandParam;
import com.mymall.product.model.PmsBrand;

import java.util.List;

public interface PmsBrandService {
    List<PmsBrand> listAllBrand();

    int createBrand(PmsBrandParam pmsBrand);

    int updateBrand(Long id, PmsBrandParam pmsBrandParam);

    int deleteBrand(Long id);

    List<PmsBrand> listBrand(String keyword, Integer pageNum, Integer pageSize);

    PmsBrand getBrand(Long id);

    int deleteBrand(List<Long> ids);

    int updateShowStatus(List<Long> ids, Integer showStatus);

    int updateFactoryStatus(List<Long> ids, Integer factoryStatus);
}
