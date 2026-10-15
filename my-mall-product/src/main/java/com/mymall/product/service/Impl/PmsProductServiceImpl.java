package com.mymall.product.service.Impl;

import com.github.pagehelper.PageHelper;
import com.mymall.product.dao.*;
import com.mymall.product.domain.PmsProductParam;
import com.mymall.product.domain.PmsProductQueryParam;
import com.mymall.product.domain.PmsProductResult;
import com.mymall.product.model.*;
import com.mymall.product.service.PmsProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class PmsProductServiceImpl implements PmsProductService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PmsProductServiceImpl.class);

    @Autowired
    private PmsProductMapper productMapper;
    @Autowired
    private PmsMemberPriceDao memberPriceDao;
    @Autowired
    private PmsProductLadderDao productLadderDao;
    @Autowired
    private PmsProductFullReductionDao productFullReductionDao;
    @Autowired
    private PmsSkuStockDao skuStockDao;
    @Autowired
    private PmsProductAttributeValueDao productAttributeValueDao;
    @Autowired
    private CmsSubjectProductRelationDao subjectProductRelationDao;
    @Autowired
    private CmsPrefrenceAreaProductRelationDao prefrenceAreaProductRelationDao;
    @Autowired
    private PmsProductVertifyRecordMapper productVertifyRecordMapper;
    @Autowired
    private PmsProductVertifyRecordDao productVertifyRecordDao;



    @Autowired
    private PmsProductCategoryMapper productCategoryMapper;
    @Autowired
    private PmsSkuStockMapper skuStockMapper;
    @Autowired
    private PmsMemberPriceMapper memberPriceMapper;
    @Autowired
    private PmsProductLadderMapper productLadderMapper;
    @Autowired
    private PmsProductFullReductionMapper productFullReductionMapper;
    @Autowired
    private PmsProductAttributeValueMapper productAttributeValueMapper;
    @Autowired
    private CmsSubjectProductRelationMapper subjectProductRelationMapper;
    @Autowired
    private CmsPrefrenceAreaProductRelationMapper prefrenceAreaProductRelationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int create(PmsProductParam productParam) {
        // ① 写商品主表
        PmsProduct product = new PmsProduct();
        BeanUtils.copyProperties(productParam, product);
        product.setPublishStatus(0);      // 默认下架
        product.setVerifyStatus(0);       // 默认未审核
        product.setSale(0);               // 销量
        product.setStock(0);              // 库存
        product.setNewStatus(0);          // 默认不是新品
        productMapper.insertSelective(product);
        Long productId = product.getId();

        // ② 写 SKU
        List<PmsSkuStock> skuList = productParam.getSkuStockList();
        if (!CollectionUtils.isEmpty(skuList)) {
            for (PmsSkuStock sku : skuList) {
                sku.setProductId(productId);
            }
            skuStockDao.insertList(skuList);
        }

        // ③ 写会员价格
        List<PmsMemberPrice> memberPriceList = productParam.getMemberPriceList();
        if (!CollectionUtils.isEmpty(memberPriceList)) {
            for (PmsMemberPrice price : memberPriceList) {
                price.setProductId(productId);
            }
            memberPriceDao.insertList(memberPriceList);
        }

        // ④ 写阶梯价格
        List<PmsProductLadder> ladderList = productParam.getProductLadderList();
        if (!CollectionUtils.isEmpty(ladderList)) {
            for (PmsProductLadder ladder : ladderList) {
                ladder.setProductId(productId);
            }
            productLadderDao.insertList(ladderList);
        }

        // ⑤ 写满减
        List<PmsProductFullReduction> fullReductionList = productParam.getProductFullReductionList();
        if (!CollectionUtils.isEmpty(fullReductionList)) {
            for (PmsProductFullReduction reduction : fullReductionList) {
                reduction.setProductId(productId);
            }
            productFullReductionDao.insertList(fullReductionList);
        }

        // ⑥ 写属性值
        List<PmsProductAttributeValue> attrValueList = productParam.getProductAttributeValueList();
        if (!CollectionUtils.isEmpty(attrValueList)) {
            for (PmsProductAttributeValue attr : attrValueList) {
                attr.setProductId(productId);
            }
            productAttributeValueDao.insertList(attrValueList);
        }

        // ⑦ 写专题关联
        List<CmsSubjectProductRelation> subjectRelationList = productParam.getSubjectProductRelationList();
        if (!CollectionUtils.isEmpty(subjectRelationList)) {
            for (CmsSubjectProductRelation relation : subjectRelationList) {
                relation.setProductId(productId);
            }
            subjectProductRelationDao.insertList(subjectRelationList);
        }

        // ⑧ 写优选关联
        List<CmsPrefrenceAreaProductRelation> prefrenceRelationList = productParam.getPrefrenceAreaProductRelationList();
        if (!CollectionUtils.isEmpty(prefrenceRelationList)) {
            for (CmsPrefrenceAreaProductRelation relation : prefrenceRelationList) {
                relation.setProductId(productId);
            }
            prefrenceAreaProductRelationDao.insertList(prefrenceRelationList);
        }

        // ⑨ 写审核记录
        PmsProductVertifyRecord record = new PmsProductVertifyRecord();
        record.setProductId(productId);
        record.setCreateTime(new Date());
        record.setStatus(0);
        record.setDetail("新品创建，待审核");
        productVertifyRecordMapper.insertSelective(record);

        return 1;
    }

    /**
     * 商品编辑回显：查单个商品的完整信息（主表 + 6 张子表 + 分类父ID）。
     *
     * 实现方式：拆成 8 次单表查询，在 Service 层组装，替代原项目的 7 表大 JOIN。
     * 原因：多表 LEFT JOIN 会产生笛卡尔积（如 3 SKU × 3 会员价 × 2 阶梯 = 36 行重复数据），
     *       且 detail_html 等大字段会在每行重复传输；拆单表后每表走 product_id 索引，
     *       数据量再大也稳定，且各子表独立、便于后续加缓存。
     *
     * 查询顺序：①主表 ②分类父ID ③SKU ④会员价 ⑤阶梯价 ⑥满减 ⑦属性值 ⑧专题关联 ⑨优选关联
     */

    @Override
    public PmsProductResult getUpdateInfo(Long id) {
        // ① 查商品主表
        PmsProduct product = productMapper.selectByPrimaryKey(id);
        if (product == null) {
            return null;
        }
        // 把主表字段拷贝到返回对象
        PmsProductResult result = new PmsProductResult();
        BeanUtils.copyProperties(product, result);

        // ② 查分类父ID（只连一张分类表，不会笛卡尔积）
        PmsProductCategory category = productCategoryMapper.selectByPrimaryKey(product.getProductCategoryId());
        if (category != null) {
            result.setCateParentId(category.getParentId());
        }

        // ③ SKU 库存
        PmsSkuStockExample skuExample = new PmsSkuStockExample();
        skuExample.createCriteria().andProductIdEqualTo(id);
        result.setSkuStockList(skuStockMapper.selectByExample(skuExample));

        // ④ 会员价格
        PmsMemberPriceExample mpExample = new PmsMemberPriceExample();
        mpExample.createCriteria().andProductIdEqualTo(id);
        result.setMemberPriceList(memberPriceMapper.selectByExample(mpExample));

        // ⑤ 阶梯价格
        PmsProductLadderExample ladderExample = new PmsProductLadderExample();
        ladderExample.createCriteria().andProductIdEqualTo(id);
        result.setProductLadderList(productLadderMapper.selectByExample(ladderExample));

        // ⑥ 满减
        PmsProductFullReductionExample frExample = new PmsProductFullReductionExample();
        frExample.createCriteria().andProductIdEqualTo(id);
        result.setProductFullReductionList(productFullReductionMapper.selectByExample(frExample));

        // ⑦ 商品属性值
        PmsProductAttributeValueExample attrExample = new PmsProductAttributeValueExample();
        attrExample.createCriteria().andProductIdEqualTo(id);
        result.setProductAttributeValueList(productAttributeValueMapper.selectByExample(attrExample));

        // ⑧ 专题关联
        CmsSubjectProductRelationExample subjectExample = new CmsSubjectProductRelationExample();
        subjectExample.createCriteria().andProductIdEqualTo(id);
        result.setSubjectProductRelationList(subjectProductRelationMapper.selectByExample(subjectExample));

        // ⑨ 优选关联
        CmsPrefrenceAreaProductRelationExample prefrenceExample = new CmsPrefrenceAreaProductRelationExample();
        prefrenceExample.createCriteria().andProductIdEqualTo(id);
        result.setPrefrenceAreaProductRelationList(prefrenceAreaProductRelationMapper.selectByExample(prefrenceExample));

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(Long id, PmsProductParam productParam) {
        // ① 更新商品主表
        productParam.setId(id);
        productMapper.updateByPrimaryKeySelective(productParam);

        // ② 会员价格：先删旧，再插新（list 为 null 则不动）
        List<PmsMemberPrice> memberPriceList = productParam.getMemberPriceList();
        if (memberPriceList != null) {
            PmsMemberPriceExample mpExample = new PmsMemberPriceExample();
            mpExample.createCriteria().andProductIdEqualTo(id);
            memberPriceMapper.deleteByExample(mpExample);
            if (!CollectionUtils.isEmpty(memberPriceList)) {
                for (PmsMemberPrice item : memberPriceList) {
                    item.setProductId(id);
                }
                memberPriceDao.insertList(memberPriceList);
            }
        }

        // ③ 阶梯价格
        List<PmsProductLadder> ladderList = productParam.getProductLadderList();
        if (ladderList != null) {
            PmsProductLadderExample ladderExample = new PmsProductLadderExample();
            ladderExample.createCriteria().andProductIdEqualTo(id);
            productLadderMapper.deleteByExample(ladderExample);
            if (!CollectionUtils.isEmpty(ladderList)) {
                for (PmsProductLadder item : ladderList) {
                    item.setProductId(id);
                }
                productLadderDao.insertList(ladderList);
            }
        }

        // ④ 满减价格
        List<PmsProductFullReduction> fullReductionList = productParam.getProductFullReductionList();
        if (fullReductionList != null) {
            PmsProductFullReductionExample frExample = new PmsProductFullReductionExample();
            frExample.createCriteria().andProductIdEqualTo(id);
            productFullReductionMapper.deleteByExample(frExample);
            if (!CollectionUtils.isEmpty(fullReductionList)) {
                for (PmsProductFullReduction item : fullReductionList) {
                    item.setProductId(id);
                }
                productFullReductionDao.insertList(fullReductionList);
            }
        }

        // ⑤ SKU 库存
        handleUpdateSkuStockList(id, productParam);

        // ⑥ 商品属性值
        List<PmsProductAttributeValue> attrValueList = productParam.getProductAttributeValueList();
        if (attrValueList != null) {
            PmsProductAttributeValueExample attrExample = new PmsProductAttributeValueExample();
            attrExample.createCriteria().andProductIdEqualTo(id);
            productAttributeValueMapper.deleteByExample(attrExample);
            if (!CollectionUtils.isEmpty(attrValueList)) {
                for (PmsProductAttributeValue item : attrValueList) {
                    item.setProductId(id);
                }
                productAttributeValueDao.insertList(attrValueList);
            }
        }

        // ⑦ 专题关联
        List<CmsSubjectProductRelation> subjectList = productParam.getSubjectProductRelationList();
        if (subjectList != null) {
            CmsSubjectProductRelationExample subjectExample = new CmsSubjectProductRelationExample();
            subjectExample.createCriteria().andProductIdEqualTo(id);
            subjectProductRelationMapper.deleteByExample(subjectExample);
            if (!CollectionUtils.isEmpty(subjectList)) {
                for (CmsSubjectProductRelation item : subjectList) {
                    item.setProductId(id);
                }
                subjectProductRelationDao.insertList(subjectList);
            }
        }

        // ⑧ 优选关联
        List<CmsPrefrenceAreaProductRelation> prefrenceList = productParam.getPrefrenceAreaProductRelationList();
        if (prefrenceList != null) {
            CmsPrefrenceAreaProductRelationExample prefrenceExample = new CmsPrefrenceAreaProductRelationExample();
            prefrenceExample.createCriteria().andProductIdEqualTo(id);
            prefrenceAreaProductRelationMapper.deleteByExample(prefrenceExample);
            if (!CollectionUtils.isEmpty(prefrenceList)) {
                for (CmsPrefrenceAreaProductRelation item : prefrenceList) {
                    item.setProductId(id);
                }
                prefrenceAreaProductRelationDao.insertList(prefrenceList);
            }
        }

        return 1;
    }

    @Override
    public List<PmsProduct> list(PmsProductQueryParam productQueryParam, Integer pageSize, Integer pageNum) {
        PageHelper.startPage(pageNum, pageSize);
        PmsProductExample productExample = new PmsProductExample();
        PmsProductExample.Criteria criteria = productExample.createCriteria();
        criteria.andDeleteStatusEqualTo(0);
        if (productQueryParam.getPublishStatus() != null) {
            criteria.andPublishStatusEqualTo(productQueryParam.getPublishStatus());
        }
        if (productQueryParam.getVerifyStatus() != null) {
            criteria.andVerifyStatusEqualTo(productQueryParam.getVerifyStatus());
        }
        if (!StringUtils.isEmpty(productQueryParam.getKeyword())) {
            criteria.andNameLike("%" + productQueryParam.getKeyword() + "%");
        }
        if (!StringUtils.isEmpty(productQueryParam.getProductSn())) {
            criteria.andProductSnEqualTo(productQueryParam.getProductSn());
        }
        if (productQueryParam.getBrandId() != null) {
            criteria.andBrandIdEqualTo(productQueryParam.getBrandId());
        }
        if (productQueryParam.getProductCategoryId() != null) {
            criteria.andProductCategoryIdEqualTo(productQueryParam.getProductCategoryId());
        }
        productExample.setOrderByClause("id desc");//最新在前
        return productMapper.selectByExample(productExample);
    }

    @Override
    public List<PmsProduct> list(String keyword) {
        PmsProductExample productExample = new PmsProductExample();
        PmsProductExample.Criteria criteria = productExample.createCriteria();
        criteria.andDeleteStatusEqualTo(0);
        if(!StringUtils.isEmpty(keyword)){
            criteria.andNameLike("%" + keyword + "%");
            productExample.or().andDeleteStatusEqualTo(0).andProductSnLike("%" + keyword + "%");
        }
        return productMapper.selectByExample(productExample);
    }

    /**
     * 批量修改商品审核状态，并记录审核日志。
     * 原：**查不到真实审核人**，全是 "test"
     *
     * 优化点：
     * 1. @Transactional：先改状态再插记录，两步必须同成功同回滚；
     * 2. ids 判空：防止空列表导致 andIdIn() 更新全表；
     * 3. vertifyMan 从登录态取：不写死 "test"，记录真实审核人；
     * 4. new Date() 提到循环外：避免每个记录重复创建对象；
     * 5. 批量插入审核记录：用 insertList 一次 SQL 写完，不用循环单条插。
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateVerifyStatus(List<Long> ids, Integer verifyStatus, String detail) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        // 批量更新审核状态
        PmsProduct product = new PmsProduct();
        product.setVerifyStatus(verifyStatus);
        PmsProductExample example = new PmsProductExample();
        example.createCriteria().andIdIn(ids);
        int count = productMapper.updateByExampleSelective(product, example);

        // 批量插入审核记录
        Date now = new Date();
//        String currentUser = StpUtil.getLoginIdAsString();  // 从登录态取
        List<PmsProductVertifyRecord> list = new ArrayList<>();
        for (Long id : ids) {
            PmsProductVertifyRecord record = new PmsProductVertifyRecord();
            record.setProductId(id);
            record.setCreateTime(now);
            record.setDetail(detail);
            record.setStatus(verifyStatus);
            record.setVertifyMan("test");
            list.add(record);
        }
        productVertifyRecordDao.insertList(list);
        return count;
    }

    @Override
    public int updatePublishStatus(List<Long> ids, Integer publishStatus) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        PmsProduct record = new PmsProduct();
        record.setPublishStatus(publishStatus);
        PmsProductExample example = new PmsProductExample();
        example.createCriteria().andIdIn(ids);
        return productMapper.updateByExampleSelective(record, example);
    }

    @Override
    public int updateRecommendStatus(List<Long> ids, Integer recommendStatus) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        PmsProduct record = new PmsProduct();
        record.setRecommandStatus(recommendStatus);
        PmsProductExample example = new PmsProductExample();
        example.createCriteria().andIdIn(ids);
        return productMapper.updateByExampleSelective(record, example);
    }

    @Override
    public int updateNewStatus(List<Long> ids, Integer newStatus) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        PmsProduct record = new PmsProduct();
        record.setNewStatus(newStatus);
        PmsProductExample example = new PmsProductExample();
        example.createCriteria().andIdIn(ids);
        return productMapper.updateByExampleSelective(record, example);
    }

    @Override
    public int updateDeleteStatus(List<Long> ids, Integer deleteStatus) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        PmsProduct record = new PmsProduct();
        record.setDeleteStatus(deleteStatus);
        PmsProductExample example = new PmsProductExample();
        example.createCriteria().andIdIn(ids);
        return productMapper.updateByExampleSelective(record, example);
    }


    private void handleUpdateSkuStockList(Long id, PmsProductParam productParam) {
        // 空列表直接返回
        if (CollectionUtils.isEmpty(productParam.getSkuStockList())) return;

        for (int i = 0; i < productParam.getSkuStockList().size(); i++) {
            PmsSkuStock skuStock = productParam.getSkuStockList().get(i);

            // 如果用户没填 SKU 编码，就自动生成
            if (StringUtils.isEmpty(skuStock.getSkuCode())) {
                // 格式：日期(8位) + 商品ID(4位) + 序号(3位)
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                StringBuilder sb = new StringBuilder();
                sb.append(sdf.format(new Date()));              // 20260929
                sb.append(String.format("%04d", productParam.getId()));     // 0003（商品ID=3）
                sb.append(String.format("%03d", i + 1));        // 001（第1个SKU）
                skuStock.setSkuCode(sb.toString());
                // 结果：202609290003001
            }
        }

    }
}


