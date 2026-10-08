package com.mymall.portal.service;

import com.mymall.portal.domain.MemberReadHistory;
import org.springframework.data.domain.Page;

import java.util.List;

public interface MemberReadHistoryService {
    int create(MemberReadHistory memberReadHistory);

    int delete(List<String> ids);

    void clear();

    Page<MemberReadHistory> list(Integer pageNum, Integer pageSize);
}
