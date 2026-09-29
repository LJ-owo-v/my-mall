package com.mymall.product.dao;

import com.mymall.product.model.PmsSkuStock;

import java.util.List;

public interface PmsSkuStockDao {
    int replaceList(List<PmsSkuStock> skuStockList);

    int insertList(List<PmsSkuStock> skuList);
}
