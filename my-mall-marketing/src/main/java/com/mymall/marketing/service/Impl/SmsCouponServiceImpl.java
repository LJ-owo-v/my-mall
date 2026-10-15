package com.mymall.marketing.service.Impl;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.github.pagehelper.PageHelper;
import com.mymall.marketing.dao.*;
import com.mymall.marketing.domain.SmsCouponParam;
import com.mymall.marketing.model.*;
import com.mymall.marketing.service.SmsCouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class SmsCouponServiceImpl  implements SmsCouponService{

    @Autowired
    private SmsCouponMapper couponMapper;
    @Autowired
    private SmsCouponProductRelationMapper productRelationMapper;
    @Autowired
    private SmsCouponProductCategoryRelationMapper productCategoryRelationMapper;

    @Autowired
    private SmsCouponProductRelationDao productRelationDao;
    @Autowired
    private SmsCouponProductCategoryRelationDao productCategoryRelationDao;
    @Autowired
    private SmsCouponDao couponDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int create(SmsCouponParam couponParam) {
        couponParam.setCount(couponParam.getPublishCount());
        couponParam.setUseCount(0);
        couponParam.setReceiveCount(0);

        // 插入优惠券主表
        int count = couponMapper.insert(couponParam);

        // useType=2：指定商品
        if (couponParam.getUseType().equals(2)
                && !CollectionUtils.isEmpty(couponParam.getProductRelationList())) {
            for (SmsCouponProductRelation relation : couponParam.getProductRelationList()) {
                relation.setCouponId(couponParam.getId());
            }
            productRelationDao.insertList(couponParam.getProductRelationList());
        }

        // useType=1：指定分类
        if (couponParam.getUseType().equals(1)
                && !CollectionUtils.isEmpty(couponParam.getProductCategoryRelationList())) {
            for (SmsCouponProductCategoryRelation relation : couponParam.getProductCategoryRelationList()) {
                relation.setCouponId(couponParam.getId());
            }
            productCategoryRelationDao.insertList(couponParam.getProductCategoryRelationList());
        }

        return count;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        //删除优惠券
        int count = couponMapper.deleteByPrimaryKey(id);
        //删除商品关联
        deleteProductRelation(id);
        //删除商品分类关联
        deleteProductCategoryRelation(id);
        return count;
    }

    private void deleteProductCategoryRelation(Long id) {
        SmsCouponProductCategoryRelationExample productCategoryRelationExample = new SmsCouponProductCategoryRelationExample();
        productCategoryRelationExample.createCriteria().andCouponIdEqualTo(id);
        productCategoryRelationMapper.deleteByExample(productCategoryRelationExample);
    }

    private void deleteProductRelation(Long id) {
        SmsCouponProductRelationExample productRelationExample = new SmsCouponProductRelationExample();
        productRelationExample.createCriteria().andCouponIdEqualTo(id);
        productRelationMapper.deleteByExample(productRelationExample);

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(Long id, SmsCouponParam couponParam) {
        couponParam.setId(id);
        int count = couponMapper.updateByPrimaryKey(couponParam);

        if (couponParam.getUseType().equals(2)
                && !CollectionUtils.isEmpty(couponParam.getProductRelationList())) {
            for (SmsCouponProductRelation relation : couponParam.getProductRelationList()) {
                relation.setCouponId(id);
            }
            deleteProductRelation(id);
            productRelationDao.insertList(couponParam.getProductRelationList());
        }

        if (couponParam.getUseType().equals(1)
                && !CollectionUtils.isEmpty(couponParam.getProductCategoryRelationList())) {
            for (SmsCouponProductCategoryRelation relation : couponParam.getProductCategoryRelationList()) {
                relation.setCouponId(id);
            }
            deleteProductCategoryRelation(id);
            productCategoryRelationDao.insertList(couponParam.getProductCategoryRelationList());
        }

        return count;
    }


    @Override
    public List<SmsCoupon> list(String name, Integer type, Integer pageSize, Integer pageNum) {
        SmsCouponExample example = new SmsCouponExample();
        SmsCouponExample.Criteria criteria = example.createCriteria();
        if(!StringUtils.isEmpty(name)){
            criteria.andNameLike("%"+name+"%");
        }
        if(type!=null){
            criteria.andTypeEqualTo(type);
        }
        PageHelper.startPage(pageNum,pageSize);
        return couponMapper.selectByExample(example);
    }


    @Override
    public SmsCouponParam getItem(Long id) {
        return couponDao.getItem(id);
    }
}
