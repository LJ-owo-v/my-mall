package com.mymall.product.service.Impl;

import com.github.pagehelper.PageHelper;
import com.mymall.product.dao.*;
import com.mymall.product.domain.PmsProductCategoryParam;
import com.mymall.product.domain.PmsProductCategoryWithChildrenItem;
import com.mymall.product.model.*;
import com.mymall.product.service.PmsProductCategoryService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class PmsProductCategoryImpl  implements PmsProductCategoryService {
    @Autowired
    private PmsProductCategoryMapper productCategoryMapper;
    @Autowired
    private PmsProductCategoryAttributeRelationMapper productCategoryAttributeRelationMapper;
    @Autowired
    private PmsProductCategoryAttributeRelationDao productCategoryAttributeRelationDao;
    @Autowired
    private PmsProductMapper productMapper;
    @Autowired
    private PmsProductCategoryDao productCategoryDao;


    /**
     * pms_product_category + pms_product_category_attribute_relation两表操作
     * @param pmsProductCategoryParam
     * @return
     */
    @Transactional
    @Override
    public int create(PmsProductCategoryParam pmsProductCategoryParam) {
        PmsProductCategory productCategory = new PmsProductCategory();
        productCategory.setProductCount(0);
        BeanUtils.copyProperties(pmsProductCategoryParam, productCategory);
        //没有父分类时为一级分类
        setCategoryLevel(productCategory);
        int count = productCategoryMapper.insertSelective(productCategory);
        //创建筛选属性关联
        List<Long> productAttributeIdList = pmsProductCategoryParam.getProductAttributeIdList();
        if(!CollectionUtils.isEmpty(productAttributeIdList)){
            insertRelationList(productCategory.getId(), productAttributeIdList);
        }
        return count;
    }

    /**
     * pms_product_category + 关系表先删后插
     * @param id
     * @param pmsProductCategoryParam
     * @return
     */
    @Transactional
    @Override
    public int update(Long id, PmsProductCategoryParam pmsProductCategoryParam) {
        PmsProductCategory productCategory = new PmsProductCategory();
        productCategory.setId(id);
        BeanUtils.copyProperties(pmsProductCategoryParam, productCategory);
        setCategoryLevel(productCategory);
        //更新商品分类时要更新商品中的名称
        PmsProduct product = new PmsProduct();
        product.setProductCategoryName(productCategory.getName());
        PmsProductExample example = new PmsProductExample();
        example.createCriteria().andProductCategoryIdEqualTo(id);
        productMapper.updateByExampleSelective(product,example);
        //同时更新筛选属性的信息
        if(!CollectionUtils.isEmpty(pmsProductCategoryParam.getProductAttributeIdList())){
            PmsProductCategoryAttributeRelationExample relationExample = new PmsProductCategoryAttributeRelationExample();
            relationExample.createCriteria().andProductCategoryIdEqualTo(id);
            productCategoryAttributeRelationMapper.deleteByExample(relationExample);
            insertRelationList(id,pmsProductCategoryParam.getProductAttributeIdList());
        }else{
            PmsProductCategoryAttributeRelationExample relationExample = new PmsProductCategoryAttributeRelationExample();
            relationExample.createCriteria().andProductCategoryIdEqualTo(id);
            productCategoryAttributeRelationMapper.deleteByExample(relationExample);
        }
        return productCategoryMapper.updateByPrimaryKeySelective(productCategory);
    }

    /**
     * 分页获取商品分类
     * @param parentId
     * @param pageSize
     * @param pageNum
     */
    @Override
    public List<PmsProductCategory> getList(Long parentId, Integer pageSize, Integer pageNum) {
        PageHelper.startPage(pageNum, pageSize);
        PmsProductCategoryExample example = new PmsProductCategoryExample();
        example.setOrderByClause("sort desc");
        example.createCriteria().andParentIdEqualTo(parentId);
        return productCategoryMapper.selectByExample(example);
    }

    /**
     * 获取商品分类详情
     * @param id
     */
    @Override
    public PmsProductCategory getItem(Long id) {
        return productCategoryMapper.selectByPrimaryKey(id);
    }

    /**
     * 删除商品分类
     * @param id
     */
    @Override
    public int delete(Long id) {
        return productCategoryMapper.deleteByPrimaryKey(id);
    }

    /**
     * 批量更新导航状态
     */
    @Override
    public int updateNavStatus(List<Long> ids, Integer navStatus) {
        PmsProductCategory productCategory = new PmsProductCategory();
        productCategory.setNavStatus(navStatus);
        PmsProductCategoryExample example = new PmsProductCategoryExample();
        example.createCriteria().andIdIn(ids);
        return productCategoryMapper.updateByExampleSelective(productCategory, example);
    }

    @Override
    public int updateShowStatus(List<Long> ids, Integer showStatus) {
        PmsProductCategory productCategory = new PmsProductCategory();
        productCategory.setShowStatus(showStatus);
        PmsProductCategoryExample example = new PmsProductCategoryExample();
        example.createCriteria().andIdIn(ids);
        return productCategoryMapper.updateByExampleSelective(productCategory, example);
    }

    @Override
    public List<PmsProductCategoryWithChildrenItem> listWithChildren() {
        return productCategoryDao.listWithChildren();
    }

    /**
     * 批量插入商品分类与筛选属性关系表
     * @param productCategoryId 商品分类id
     * @param productAttributeIdList 相关商品筛选属性id集合
     */
    private void insertRelationList(Long productCategoryId, List<Long> productAttributeIdList) {
        List<PmsProductCategoryAttributeRelation> relationList = new ArrayList<>();
        for (Long productAttrId : productAttributeIdList) {
            PmsProductCategoryAttributeRelation relation = new PmsProductCategoryAttributeRelation();
            relation.setProductAttributeId(productAttrId);
            relation.setProductCategoryId(productCategoryId);
            relationList.add(relation);
        }
        productCategoryAttributeRelationDao.insertList(relationList);
    }

    /**
     * 根据分类的parentId设置分类的level
     */
    private void setCategoryLevel(PmsProductCategory productCategory) {
        //没有父分类时为一级分类
        if (productCategory.getParentId() == 0) {
            productCategory.setLevel(0);
        } else {
            //有父分类时选择根据父分类level设置
            PmsProductCategory parentCategory = productCategoryMapper.selectByPrimaryKey(productCategory.getParentId());
            if (parentCategory != null) {
                productCategory.setLevel(parentCategory.getLevel() + 1);
            } else {
                productCategory.setLevel(0);
            }
        }
    }
}
