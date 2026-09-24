package com.mymall.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.github.pagehelper.PageHelper;
import com.mymall.common.constant.AuthConstant;
import com.mymall.common.redis.RedisService;
import com.mymall.user.dao.UmsResourceMapper;
import com.mymall.user.model.UmsResource;
import com.mymall.user.model.UmsResourceExample;
import com.mymall.user.service.UmsResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class UmsResourceServiceImpl implements UmsResourceService {
    @Autowired
    private UmsResourceMapper resourceMapper;
    @Autowired
    private RedisService redisService;
    /**
     * 网关侧匹配的路径前缀。绝不能用 spring.application.name（那是 my-mall-user，会让权限表 key 全错）
     */
    @Value("${my-mall.gateway.path-prefix:/mall-admin}")
    private String pathPrefix;

    @Override
    public int create(UmsResource umsResource) {
        umsResource.setCreateTime(new Date());
        int count = resourceMapper.insert(umsResource);
        initPathResourceMap();
        return count;
    }

    @Override
    public int update(Long id, UmsResource umsResource) {
        umsResource.setId(id);
        int count = resourceMapper.updateByPrimaryKeySelective(umsResource);
        initPathResourceMap();
        return count;
    }

    @Override
    public Map<String, String> initPathResourceMap() {
        // 1. 用 TreeMap（自动按 key 字典序排序，方便对比和排查）
        Map<String, String> pathResourceMap = new TreeMap<>();

        // 2. 查出数据库里所有资源（ums_resource 表全量）
        List<UmsResource> resourceList = resourceMapper.selectByExample(new UmsResourceExample());

        // 3. 遍历每个资源，拼出 "路径 → 权限标识" 的映射
        for (UmsResource resource : resourceList) {
            // key：网关路径前缀 + 资源URL
            //   例：pathPrefix="/mall-admin"，resource.getUrl()="/product/create"
            //   → key="/mall-admin/product/create"
            // value：资源ID + ":" + 资源名
            //   例：resource.getId()=5，resource.getName()="商品创建"
            //   → value="5:商品创建"
            pathResourceMap.put(pathPrefix + resource.getUrl(),
                    resource.getId() + ":" + resource.getName());
        }

        // 4. 先删除 Redis 里旧的映射表（Hash 结构）
        redisService.del(AuthConstant.PATH_RESOURCE_MAP);

        // 5. 把新的映射表全量写入 Redis
        //    key=auth:pathResourceMap，field=路径，value=权限标识
        redisService.hSetAll(AuthConstant.PATH_RESOURCE_MAP, pathResourceMap);

        return pathResourceMap;
    }


    @Override
    public UmsResource getItem(Long id) {
        return resourceMapper.selectByPrimaryKey(id);
    }

    @Override
    public int delete(Long id) {
        int count = resourceMapper.deleteByPrimaryKey(id);
        initPathResourceMap();
        return count;
    }

    @Override
    public List<UmsResource> list(Long categoryId, String nameKeyword, String urlKeyword, Integer pageSize, Integer pageNum) {
        PageHelper.startPage(pageNum,pageSize);
        UmsResourceExample example = new UmsResourceExample();
        UmsResourceExample.Criteria criteria = example.createCriteria();
        if(categoryId!=null){
            criteria.andCategoryIdEqualTo(categoryId);
        }
        if(StrUtil.isNotEmpty(nameKeyword)){
            criteria.andNameLike('%'+nameKeyword+'%');
        }
        if(StrUtil.isNotEmpty(urlKeyword)){
            criteria.andUrlLike('%'+urlKeyword+'%');
        }
        return resourceMapper.selectByExample(example);
    }

    @Override
    public List<UmsResource> listAll() {
        return resourceMapper.selectByExample(new UmsResourceExample());
    }
}