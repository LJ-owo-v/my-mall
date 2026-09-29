package com.mymall.product.service.Impl;

import com.github.pagehelper.PageHelper;
import com.mymall.product.dao.PmsProductAttributeCategoryMapper;
import com.mymall.product.dao.PmsProductAttributeDao;
import com.mymall.product.dao.PmsProductAttributeMapper;
import com.mymall.product.domain.PmsProductAttributeParam;
import com.mymall.product.domain.ProductAttrInfo;
import com.mymall.product.model.PmsProductAttribute;
import com.mymall.product.model.PmsProductAttributeCategory;
import com.mymall.product.model.PmsProductAttributeExample;
import com.mymall.product.service.PmsProductAttributeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PmsProductAttributeServiceImpl implements PmsProductAttributeService {
    @Autowired
    private PmsProductAttributeMapper productAttributeMapper;
    @Autowired
    private PmsProductAttributeCategoryMapper productAttributeCategoryMapper;
    @Autowired
    private PmsProductAttributeDao productAttributeDao;

    @Override
    public List<PmsProductAttribute> getList(Long cid, Integer type, Integer pageSize, Integer pageNum) {
        PageHelper.startPage(pageNum, pageSize);
        PmsProductAttributeExample example = new PmsProductAttributeExample();
        example.setOrderByClause("sort desc");
        example.createCriteria().andProductAttributeCategoryIdEqualTo(cid).andTypeEqualTo(type);
        return productAttributeMapper.selectByExample(example);
    }

    @Override
    @Transactional
    public int create(PmsProductAttributeParam pmsProductAttributeParam) {
        PmsProductAttribute pmsProductAttribute = new PmsProductAttribute();
        BeanUtils.copyProperties(pmsProductAttributeParam, pmsProductAttribute);
        // ① 插入属性主表
        int count = productAttributeMapper.insertSelective(pmsProductAttribute);
        //新增商品属性以后需要更新商品属性分类数量
        PmsProductAttributeCategory pmsProductAttributeCategory = productAttributeCategoryMapper.selectByPrimaryKey(pmsProductAttribute.getProductAttributeCategoryId());
        // ② 更新分类的冗余计数字段
        if(pmsProductAttribute.getType()==0){
            pmsProductAttributeCategory.setAttributeCount(pmsProductAttributeCategory.getAttributeCount()+1);
        }else if(pmsProductAttribute.getType()==1){
            pmsProductAttributeCategory.setParamCount(pmsProductAttributeCategory.getParamCount()+1);
        }
        productAttributeCategoryMapper.updateByPrimaryKey(pmsProductAttributeCategory);
        return count;
    }

    @Override
    public int update(Long id, PmsProductAttributeParam productAttributeParam) {
        PmsProductAttribute pmsProductAttribute = new PmsProductAttribute();
        pmsProductAttribute.setId(id);
        BeanUtils.copyProperties(productAttributeParam, pmsProductAttribute);
        return productAttributeMapper.updateByPrimaryKeySelective(pmsProductAttribute);
    }

    @Override
    public PmsProductAttribute getItem(Long id) {
        return productAttributeMapper.selectByPrimaryKey(id);
    }

    @Override
    @Transactional
    public int delete(List<Long> ids) {
        // ① 先查第一条属性，拿到 type 和它属于哪个分类
        // （假设批量删除的都是同一个分类下的属性）
        PmsProductAttribute attr = productAttributeMapper.selectByPrimaryKey(ids.get(0));
        Integer type = attr.getType();  // 0=规格属性，1=参数

        // ② 查出对应的属性分类
        PmsProductAttributeCategory category =
                productAttributeCategoryMapper.selectByPrimaryKey(attr.getProductAttributeCategoryId());

        // ③ 批量删除属性
        PmsProductAttributeExample example = new PmsProductAttributeExample();
        example.createCriteria().andIdIn(ids);
        int count = productAttributeMapper.deleteByExample(example);  // 删了几条

        // ④ 根据 type，减少分类的冗余计数
        if (type == 0) {
            // 规格属性：attributeCount 减去删除条数
            if (category.getAttributeCount() >= count) {
                category.setAttributeCount(category.getAttributeCount() - count);
            } else {
                category.setAttributeCount(0);  // 防止负数
            }
        } else if (type == 1) {
            // 参数：paramCount 减去删除条数
            if (category.getParamCount() >= count) {
                category.setParamCount(category.getParamCount() - count);
            } else {
                category.setParamCount(0);
            }
        }

        // ⑤ 更新分类
        productAttributeCategoryMapper.updateByPrimaryKey(category);
        return count;
    }

    /**
     * 根据分类获取属性  三表联查
     */
    @Override
    public List<ProductAttrInfo> getProductAttrInfo(Long productCategoryId) {
        return productAttributeDao.getProductAttrInfo(productCategoryId);
    }

}
