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

    /** 重建权限映射表时用的临时 key 后缀：写满后 RENAME 到正式 key，避免 del/hSetAll 之间的空窗 */
    private static final String REBUILD_KEY_SUFFIX = ":rebuild";

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

        // 4. 原子替换：先写临时 key，再用 RENAME 整体覆盖正式 key
        //    【为什么不能 del + hSetAll】两条命令之间 key 不存在，网关 matchPermission 读到空表
        //    → needPermissionList 为空 → 整段接口级权限校验被跳过 → 所有管理端接口短暂放行。
        //    RENAME 是 Redis 单线程内的原子操作，网关要么读到旧表、要么读到新表，不存在空窗。
        String tmpKey = AuthConstant.PATH_RESOURCE_MAP + REBUILD_KEY_SUFFIX;
        if (pathResourceMap.isEmpty()) {
            // Redis 不保存空 Hash：putAll 空 map 等于什么都没写，紧接着 RENAME 会因源 key 不存在而报错。
            // 这里直接删正式 key（网关侧会 fail-close 并在日志里报出"映射表为空"），不写空结构。
            redisService.del(AuthConstant.PATH_RESOURCE_MAP);
            return pathResourceMap;
        }
        redisService.del(tmpKey);                        // 清掉上次异常中断可能留下的残留
        redisService.hSetAll(tmpKey, pathResourceMap);   // key=临时 key，field=路径，value=权限标识
        redisService.rename(tmpKey, AuthConstant.PATH_RESOURCE_MAP);

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