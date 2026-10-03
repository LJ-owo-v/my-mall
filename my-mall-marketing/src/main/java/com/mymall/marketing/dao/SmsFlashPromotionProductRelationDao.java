package com.mymall.marketing.dao;

import com.mymall.marketing.domain.SmsFlashPromotionProduct;
import com.mymall.marketing.model.SmsFlashPromotionProductRelation;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface SmsFlashPromotionProductRelationDao {
    // Dao接口
    List<Map<String, Object>> countBySessionId(@Param("flashPromotionId") Long flashPromotionId);

    void insertList(List<SmsFlashPromotionProductRelation> relationList);

    List<SmsFlashPromotionProduct> getList(Long flashPromotionId, Long flashPromotionSessionId);
}
