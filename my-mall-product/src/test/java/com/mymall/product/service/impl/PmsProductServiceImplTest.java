package com.mymall.product.service.impl;

import com.mymall.product.dao.*;
import com.mymall.product.domain.PmsProductParam;
import com.mymall.product.model.*;
// 生产代码的包是 service.Impl（大写 I），测试目录统一小写 impl
import com.mymall.product.service.Impl.PmsProductServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PmsProductServiceImpl 纯单元测试。
 *
 * <p>这个类有 18 个 @Autowired 依赖，覆盖率不是重点 —— 重点是<b>写库的调用关系</b>：
 * 一张商品主表 + 6 张子表，哪张表该写、什么情况下必须跳过、子表是否回填了 productId。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PmsProductServiceImpl")
class PmsProductServiceImplTest {

    @Mock private PmsProductMapper productMapper;
    @Mock private PmsMemberPriceDao memberPriceDao;
    @Mock private PmsProductLadderDao productLadderDao;
    @Mock private PmsProductFullReductionDao productFullReductionDao;
    @Mock private PmsSkuStockDao skuStockDao;
    @Mock private PmsProductAttributeValueDao productAttributeValueDao;
    @Mock private CmsSubjectProductRelationDao subjectProductRelationDao;
    @Mock private CmsPrefrenceAreaProductRelationDao prefrenceAreaProductRelationDao;
    @Mock private PmsProductVertifyRecordMapper productVertifyRecordMapper;
    @Mock private PmsProductVertifyRecordDao productVertifyRecordDao;
    @Mock private PmsProductCategoryMapper productCategoryMapper;
    @Mock private PmsSkuStockMapper skuStockMapper;
    @Mock private PmsMemberPriceMapper memberPriceMapper;
    @Mock private PmsProductLadderMapper productLadderMapper;
    @Mock private PmsProductFullReductionMapper productFullReductionMapper;
    @Mock private PmsProductAttributeValueMapper productAttributeValueMapper;
    @Mock private CmsSubjectProductRelationMapper subjectProductRelationMapper;
    @Mock private CmsPrefrenceAreaProductRelationMapper prefrenceAreaProductRelationMapper;

    @InjectMocks private PmsProductServiceImpl productService;

    @Test
    @DisplayName("create_正常发布_主表回填id且六张子表都写了productId")
    void create_正常发布_主表回填id且六张子表都写了productId() {
        PmsProductParam param = fullParam();
        stubInsertBackfillId(param);

        int count = productService.create(param);

        assertThat(count).isEqualTo(1);
        // 主表：新建商品默认下架 + 未审核 + 销量/库存清零
        ArgumentCaptor<PmsProduct> productCaptor = ArgumentCaptor.forClass(PmsProduct.class);
        verify(productMapper).insertSelective(productCaptor.capture());
        PmsProduct saved = productCaptor.getValue();
        assertThat(saved.getPublishStatus()).isZero();
        assertThat(saved.getVerifyStatus()).isZero();
        assertThat(saved.getSale()).isZero();
        assertThat(saved.getStock()).isZero();

        // 子表：每条记录都必须带上主表回填的 id，否则数据会挂到 null 上
        ArgumentCaptor<List<PmsSkuStock>> skuCaptor = ArgumentCaptor.forClass(List.class);
        verify(skuStockDao).insertList(skuCaptor.capture());
        assertThat(skuCaptor.getValue()).allSatisfy(sku -> assertThat(sku.getProductId()).isEqualTo(999L));

        verify(memberPriceDao).insertList(anyList());
        verify(productLadderDao).insertList(anyList());
        verify(productFullReductionDao).insertList(anyList());
        verify(productAttributeValueDao).insertList(anyList());
        verify(subjectProductRelationDao).insertList(anyList());
        verify(prefrenceAreaProductRelationDao).insertList(anyList());
        verify(productVertifyRecordMapper).insertSelective(any(PmsProductVertifyRecord.class));
    }

    @Test
    @DisplayName("create_SKU列表为空_不调用skuStockDao")
    void create_SKU列表为空_不调用skuStockDao() {
        PmsProductParam param = fullParam();
        param.setSkuStockList(List.of());
        stubInsertBackfillId(param);

        int count = productService.create(param);

        assertThat(count).isEqualTo(1);
        verify(skuStockDao, never()).insertList(anyList());
        // 其余子表照常写入，不能因为 SKU 为空就整体跳过
        verify(memberPriceDao).insertList(anyList());
    }

    @Test
    @DisplayName("create_阶梯价为null_不调用productLadderDao")
    void create_阶梯价为null_不调用productLadderDao() {
        PmsProductParam param = fullParam();
        param.setProductLadderList(null);
        stubInsertBackfillId(param);

        productService.create(param);

        verify(productLadderDao, never()).insertList(anyList());
    }

    @Test
    @DisplayName("create_满减为null_不调用productFullReductionDao")
    void create_满减为null_不调用productFullReductionDao() {
        PmsProductParam param = fullParam();
        param.setProductFullReductionList(null);
        stubInsertBackfillId(param);

        productService.create(param);

        verify(productFullReductionDao, never()).insertList(anyList());
    }

    @Test
    @DisplayName("update_子表先删后插")
    void update_子表先删后插() {
        PmsProductParam param = fullParam();

        int count = productService.update(999L, param);

        assertThat(count).isEqualTo(1);
        assertThat(param.getId()).isEqualTo(999L);
        verify(productMapper).updateByPrimaryKeySelective(param);
        // 会员价/阶梯价/满减/属性值/专题/优选：都是"清空后重建"
        verify(memberPriceMapper).deleteByExample(any(PmsMemberPriceExample.class));
        verify(memberPriceDao).insertList(anyList());
        verify(productLadderMapper).deleteByExample(any(PmsProductLadderExample.class));
        verify(productFullReductionMapper).deleteByExample(any(PmsProductFullReductionExample.class));
        verify(productAttributeValueMapper).deleteByExample(any(PmsProductAttributeValueExample.class));
        verify(subjectProductRelationMapper).deleteByExample(any(CmsSubjectProductRelationExample.class));
        verify(prefrenceAreaProductRelationMapper).deleteByExample(any(CmsPrefrenceAreaProductRelationExample.class));
    }

    @Test
    @DisplayName("updatePublishStatus_批量上下架_返回影响行数")
    void updatePublishStatus_批量上下架_返回影响行数() {
        when(productMapper.updateByExampleSelective(any(PmsProduct.class), any(PmsProductExample.class)))
                .thenReturn(2);

        int count = productService.updatePublishStatus(List.of(1L, 2L), 1);

        ArgumentCaptor<PmsProduct> captor = ArgumentCaptor.forClass(PmsProduct.class);
        verify(productMapper).updateByExampleSelective(captor.capture(), any(PmsProductExample.class));
        assertThat(count).isEqualTo(2);
        assertThat(captor.getValue().getPublishStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("updateDeleteStatus_软删除_只改deleteStatus不物理删")
    void updateDeleteStatus_软删除_只改deleteStatus不物理删() {
        when(productMapper.updateByExampleSelective(any(PmsProduct.class), any(PmsProductExample.class)))
                .thenReturn(1);

        int count = productService.updateDeleteStatus(List.of(1L), 1);

        ArgumentCaptor<PmsProduct> captor = ArgumentCaptor.forClass(PmsProduct.class);
        verify(productMapper).updateByExampleSelective(captor.capture(), any(PmsProductExample.class));
        assertThat(count).isEqualTo(1);
        assertThat(captor.getValue().getDeleteStatus()).isEqualTo(1);
        // 商品被订单/购物车引用，物理删除会破坏历史数据
        verify(productMapper, never()).deleteByPrimaryKey(any());
    }

    @Test
    @DisplayName("getUpdateInfo_商品不存在_返回null")
    void getUpdateInfo_商品不存在_返回null() {
        when(productMapper.selectByPrimaryKey(404L)).thenReturn(null);

        assertThat(productService.getUpdateInfo(404L)).isNull();
        // 主表查不到就该短路，不该继续查另外 8 张子表
        verifyNoInteractions(skuStockMapper, memberPriceMapper, productLadderMapper);
    }

    // ==================== 夹具 ====================

    private void stubInsertBackfillId(PmsProductParam param) {
        // 模拟 MBG 的 useGeneratedKeys：insert 后主键回填到对象上
        doAnswer(inv -> {
            PmsProduct inserted = inv.getArgument(0);
            inserted.setId(999L);
            param.setId(999L);
            return 1;
        }).when(productMapper).insertSelective(any(PmsProduct.class));
    }

    private PmsProductParam fullParam() {
        PmsProductParam param = new PmsProductParam();
        param.setName("测试商品");
        param.setProductSn("SN-001");
        param.setPrice(new BigDecimal("199"));

        PmsSkuStock sku = new PmsSkuStock();
        sku.setSkuCode("SKU-001");
        sku.setPrice(new BigDecimal("199"));
        sku.setStock(10);
        param.setSkuStockList(List.of(sku));

        PmsMemberPrice memberPrice = new PmsMemberPrice();
        memberPrice.setMemberLevelId(1L);
        memberPrice.setMemberPrice(new BigDecimal("189"));
        param.setMemberPriceList(List.of(memberPrice));

        PmsProductLadder ladder = new PmsProductLadder();
        ladder.setCount(3);
        ladder.setDiscount(new BigDecimal("0.9"));
        param.setProductLadderList(List.of(ladder));

        PmsProductFullReduction reduction = new PmsProductFullReduction();
        reduction.setFullPrice(new BigDecimal("1000"));
        reduction.setReducePrice(new BigDecimal("100"));
        param.setProductFullReductionList(List.of(reduction));

        PmsProductAttributeValue attrValue = new PmsProductAttributeValue();
        attrValue.setProductAttributeId(7L);
        attrValue.setValue("红色");
        param.setProductAttributeValueList(List.of(attrValue));

        CmsSubjectProductRelation subject = new CmsSubjectProductRelation();
        subject.setSubjectId(11L);
        param.setSubjectProductRelationList(List.of(subject));

        CmsPrefrenceAreaProductRelation prefrence = new CmsPrefrenceAreaProductRelation();
        prefrence.setPrefrenceAreaId(13L);
        param.setPrefrenceAreaProductRelationList(List.of(prefrence));

        return param;
    }
}
