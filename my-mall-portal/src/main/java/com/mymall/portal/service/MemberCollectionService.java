package com.mymall.portal.service;

import com.mymall.portal.domain.MemberProductCollection;
import org.springframework.data.domain.Page;

public interface MemberCollectionService {
    int add(MemberProductCollection productCollection);

    int delete(Long productId);

    Page<MemberProductCollection> list(Integer pageNum, Integer pageSize);

    MemberProductCollection detail(Long productId);

    void clear();
}
