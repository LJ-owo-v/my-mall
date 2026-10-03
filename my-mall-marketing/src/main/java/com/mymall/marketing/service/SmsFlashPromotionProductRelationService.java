package com.mymall.marketing.service;

import com.mymall.marketing.domain.SmsFlashPromotionProduct;
import com.mymall.marketing.model.SmsFlashPromotionProductRelation;

import java.util.List;

public interface SmsFlashPromotionProductRelationService {
    int create(List<SmsFlashPromotionProductRelation> relationList);

    int update(Long id, SmsFlashPromotionProductRelation relation);

    int delete(Long id);

    SmsFlashPromotionProductRelation getItem(Long id);

    List<SmsFlashPromotionProduct> list(Long flashPromotionId, Long flashPromotionSessionId, Integer pageSize, Integer pageNum);
}
