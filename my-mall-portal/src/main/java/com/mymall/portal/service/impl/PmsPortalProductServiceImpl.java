package com.mymall.portal.service.impl;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.github.pagehelper.PageHelper;
import com.mymall.portal.dao.*;
import com.mymall.portal.domain.PmsPortalProductDetail;
import com.mymall.portal.domain.PmsProductCategoryNode;
import com.mymall.portal.model.*;
import com.mymall.portal.service.PmsPortalProductService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PmsPortalProductServiceImpl implements PmsPortalProductService {
    @Autowired
    private PmsProductMapper productMapper;
    @Autowired
    private PmsProductCategoryMapper productCategoryMapper;
    @Autowired
    private PmsBrandMapper brandMapper;
    @Autowired
    private PmsProductAttributeMapper productAttributeMapper;
    @Autowired
    private PmsProductAttributeValueMapper productAttributeValueMapper;
    @Autowired
    private PmsSkuStockMapper skuStockMapper;
    @Autowired
    private PmsProductLadderMapper productLadderMapper;
    @Autowired
    private PmsProductFullReductionMapper productFullReductionMapper;
    @Autowired
    private PortalProductDao portalProductDao;


    @Override
    public List<PmsProduct> search(String keyword, Long brandId, Long productCategoryId, Integer pageNum, Integer pageSize, Integer sort) {
        PageHelper.startPage(pageNum, pageSize);
        PmsProductExample example = new PmsProductExample();
        PmsProductExample.Criteria criteria = example.createCriteria();
        criteria.andDeleteStatusEqualTo(0);
        if (StrUtil.isNotEmpty(keyword)) {
            criteria.andNameLike("%" + keyword + "%");
        }
        if (brandId != null) {
            criteria.andBrandIdEqualTo(brandId);
        }
        if (productCategoryId != null) {
            criteria.andProductCategoryIdEqualTo(productCategoryId);
        }
        //1->按新品；2->按销量；3->价格从低到高；4->价格从高到低
        if (sort == 1) {
            example.setOrderByClause("id desc");
        } else if (sort == 2) {
            example.setOrderByClause("sale desc");
        } else if (sort == 3) {
            example.setOrderByClause("price asc");
        } else if (sort == 4) {
            example.setOrderByClause("price desc");
        }
        return productMapper.selectByExample(example);
    }

    /**
     * 正确做法（一次查全部，内存组装）：
     *   一次查出所有分类 → 在内存中用递归组装成树
     *   → 只查1次数据库
     * @return
     */
    @Override
    public List<PmsProductCategoryNode> categoryTreeList() {
        // ① 一次查出所有分类（不分页，全量查询）
        PmsProductCategoryExample example = new PmsProductCategoryExample();
        List<PmsProductCategory> allList = productCategoryMapper.selectByExample(example);

        // ② Stream 处理：过滤出一级分类 → 递归构建子树 → 收集结果
        List<PmsProductCategoryNode> result = allList.stream()
                .filter(item -> item.getParentId().equals(0L))  // 只保留 parentId=0 的一级分类
                .map(item -> covert(item, allList))             // 对每个一级分类递归构建子节点
                .collect(Collectors.toList());                  // 收集成 List

        return result;
    }

    /**
     * 这个方法是**商品详情页的数据聚合器**：用 8 次单表查询（避免 JOIN 笛卡尔积），
     * 把商品、品牌、属性、SKU、促销、优惠券全部组装到一个对象返回，其中阶梯价 / 满减按 promotionType 条件查询避免浪费，
     * 属性值用 IN 批量查询避免 N+1。
     * @param id
     * @return
     */

    @Override
    public PmsPortalProductDetail detail(Long id) {
        // 创建返回对象（商品详情页需要的所有数据都组装到这里）
        PmsPortalProductDetail result = new PmsPortalProductDetail();

        // ① 获取商品基本信息（名称、价格、图片、副标题、促销类型等）
        PmsProduct product = productMapper.selectByPrimaryKey(id);
        result.setProduct(product);

        // ② 获取品牌信息（品牌名、Logo、简介）
        PmsBrand brand = brandMapper.selectByPrimaryKey(product.getBrandId());
        result.setBrand(brand);

        // ③ 获取商品属性列表（如"屏幕尺寸""颜色"等属性定义）
        //    通过商品的属性分类ID查该分类下所有属性
        PmsProductAttributeExample attributeExample = new PmsProductAttributeExample();
        attributeExample.createCriteria()
                .andProductAttributeCategoryIdEqualTo(product.getProductAttributeCategoryId());
        List<PmsProductAttribute> productAttributeList = productAttributeMapper.selectByExample(attributeExample);
        result.setProductAttributeList(productAttributeList);

        // ④ 获取商品属性值（如该商品的"屏幕尺寸=6.1寸""颜色=黑色"）
        //    只有属性列表不为空时才查，避免空查询
        if (CollUtil.isNotEmpty(productAttributeList)) {
            // 用 Stream 提取所有属性ID，批量查询属性值（IN 查询，避免循环查）
            List<Long> attributeIds = productAttributeList.stream()
                    .map(PmsProductAttribute::getId)
                    .collect(Collectors.toList());

            PmsProductAttributeValueExample attributeValueExample = new PmsProductAttributeValueExample();
            attributeValueExample.createCriteria()
                    .andProductIdEqualTo(product.getId())
                    .andProductAttributeIdIn(attributeIds);
            List<PmsProductAttributeValue> productAttributeValueList =
                    productAttributeValueMapper.selectByExample(attributeValueExample);
            result.setProductAttributeValueList(productAttributeValueList);
        }

        // ⑤ 获取SKU库存列表（不同规格的价格和库存，如"黑色64G""白色128G"）
        PmsSkuStockExample skuExample = new PmsSkuStockExample();
        skuExample.createCriteria().andProductIdEqualTo(product.getId());
        List<PmsSkuStock> skuStockList = skuStockMapper.selectByExample(skuExample);
        result.setSkuStockList(skuStockList);

        // ⑥ 阶梯价格（promotionType=3 时才查，如"买2件9折、买3件8折"）
        if (product.getPromotionType() == 3) {
            PmsProductLadderExample ladderExample = new PmsProductLadderExample();
            ladderExample.createCriteria().andProductIdEqualTo(product.getId());
            List<PmsProductLadder> productLadderList = productLadderMapper.selectByExample(ladderExample);
            result.setProductLadderList(productLadderList);
        }

        // ⑦ 满减价格（promotionType=4 时才查，如"满199减20"）
        if (product.getPromotionType() == 4) {
            PmsProductFullReductionExample fullReductionExample = new PmsProductFullReductionExample();
            fullReductionExample.createCriteria().andProductIdEqualTo(product.getId());
            List<PmsProductFullReduction> productFullReductionList =
                    productFullReductionMapper.selectByExample(fullReductionExample);
            result.setProductFullReductionList(productFullReductionList);
        }

        // ⑧ 获取该商品可用的优惠券（多表 JOIN 手写 SQL）
        result.setCouponList(portalProductDao.getAvailableCouponList(
                product.getId(), product.getProductCategoryId()));

        return result;
    }


    private PmsProductCategoryNode covert(PmsProductCategory item, List<PmsProductCategory> allList) {
        PmsProductCategoryNode node = new PmsProductCategoryNode();
        BeanUtils.copyProperties(item, node);  // 拷贝分类基本信息

        // 递归：找出当前分类的所有子分类，对每个子分类再调用 covert
        List<PmsProductCategoryNode> children = allList.stream()
                .filter(subItem -> subItem.getParentId().equals(item.getId()))
                .map(subItem -> covert(subItem, allList))  // 递归调用自己
                .collect(Collectors.toList());

        node.setChildren(children);
        return node;
    }


}
