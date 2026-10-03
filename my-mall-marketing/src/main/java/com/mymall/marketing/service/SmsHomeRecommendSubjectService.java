package com.mymall.marketing.service;

import com.mymall.marketing.model.SmsHomeRecommendSubject;

import java.util.List;

public interface SmsHomeRecommendSubjectService {
    int create(List<SmsHomeRecommendSubject> homeBrandList);

    int updateSort(Long id, Integer sort);

    int delete(List<Long> ids);

    int updateRecommendStatus(List<Long> ids, Integer recommendStatus);

    List<SmsHomeRecommendSubject> list(String subjectName, Integer recommendStatus, Integer pageSize, Integer pageNum);
}
