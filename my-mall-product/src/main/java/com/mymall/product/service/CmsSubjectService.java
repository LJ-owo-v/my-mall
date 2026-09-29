package com.mymall.product.service;

import com.mymall.product.model.CmsSubject;

import java.util.List;

public interface CmsSubjectService {
    List<CmsSubject> listAll();

    List<CmsSubject> list(String keyword, Integer pageNum, Integer pageSize);
}
