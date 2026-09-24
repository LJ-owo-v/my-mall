package com.mymall.user.service.impl;

import com.mymall.user.dao.UmsResourceCategoryMapper;
import com.mymall.user.model.UmsResourceCategory;
import com.mymall.user.model.UmsResourceCategoryExample;
import com.mymall.user.service.UmsResourceCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 后台资源分类管理 Service 实现 —— 与原项目 UmsResourceCategoryServiceImpl 对齐（4 个方法）。
 * ★ 必须带 @Service：漏了会导致 Controller 注入失败（NoSuchBeanDefinitionException，启动即崩）。
 */
@Service
public class UmsResourceCategoryServiceImpl implements UmsResourceCategoryService {

    @Autowired
    private UmsResourceCategoryMapper resourceCategoryMapper;

    @Override
    public List<UmsResourceCategory> listAll() {
        UmsResourceCategoryExample example = new UmsResourceCategoryExample();
        example.setOrderByClause("sort desc");
        return resourceCategoryMapper.selectByExample(example);
    }

    @Override
    public int create(UmsResourceCategory umsResourceCategory) {
        umsResourceCategory.setCreateTime(new Date());
        return resourceCategoryMapper.insert(umsResourceCategory);
    }

    @Override
    public int update(Long id, UmsResourceCategory umsResourceCategory) {
        umsResourceCategory.setId(id);
        return resourceCategoryMapper.updateByPrimaryKeySelective(umsResourceCategory);
    }

    @Override
    public int delete(Long id) {
        return resourceCategoryMapper.deleteByPrimaryKey(id);
    }
}