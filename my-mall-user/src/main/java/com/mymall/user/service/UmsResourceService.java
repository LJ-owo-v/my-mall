package com.mymall.user.service;

import com.mymall.user.model.UmsResource;

import java.util.List;
import java.util.Map;

public interface UmsResourceService {
    int create(UmsResource umsResource);

    int update(Long id, UmsResource umsResource);

    UmsResource getItem(Long id);

    int delete(Long id);

    List<UmsResource> list(Long categoryId, String nameKeyword, String urlKeyword, Integer pageSize, Integer pageNum);

    List<UmsResource> listAll();

    Map<String, String> initPathResourceMap();
}
