package com.mymall.portal.dao;

import com.mymall.portal.domain.FlashPromotionProduct;
import com.mymall.portal.model.CmsSubject;
import com.mymall.portal.model.PmsBrand;
import com.mymall.portal.model.PmsProduct;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface HomeDao {

    /**
     * 获取推荐品牌
     */
    List<PmsBrand> getRecommendBrandList(@Param("offset") Integer offset, @Param("limit") Integer limit);

    /**
     * 获取新品推荐
     */
    List<PmsProduct> getNewProductList(@Param("offset") Integer offset, @Param("limit") Integer limit);

    /**
     * 获取人气推荐
     */
    List<PmsProduct> getHotProductList(@Param("offset") Integer offset, @Param("limit") Integer limit);

    /**
     * 获取推荐专题
     */
    List<CmsSubject> getRecommendSubjectList(@Param("offset") Integer offset, @Param("limit") Integer limit);

    /**
     * 获取秒杀商品列表
     */
    List<FlashPromotionProduct> getFlashProductList(@Param("flashPromotionId") Long flashPromotionId, @Param("sessionId") Long sessionId);
}
