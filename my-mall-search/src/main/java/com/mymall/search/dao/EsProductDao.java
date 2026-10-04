package com.mymall.search.dao;

import com.mymall.search.domain.EsProduct;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 搜索商品管理自定义Dao
 */
public interface EsProductDao {
    /**
     * 获取指定ID的搜索商品
     */
    List<EsProduct> getAllEsProductList(@Param("id") Long id);



    List<EsProduct> getEsProductList(@Param("id") Long id,
                                     @Param("offset") Integer offset,
                                     @Param("pageSize") Integer pageSize);


}
