package com.mymall.marketing.service.impl;

import com.mymall.marketing.dao.*;
import com.mymall.marketing.domain.SmsCouponParam;
import com.mymall.marketing.model.*;
// 生产代码的包是 service.Impl（大写 I），测试目录统一小写 impl
import com.mymall.marketing.service.Impl.SmsCouponServiceImpl;
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

/**
 * SmsCouponServiceImpl 纯单元测试。
 *
 * <p>重点是"券类型决定写哪张关联表"：全场券只写主表，指定商品/分类券才写关联表，
 * 且关联记录必须回填券 id（否则关联会指向 null）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SmsCouponServiceImpl")
class SmsCouponServiceImplTest {

    @Mock private SmsCouponMapper couponMapper;
    @Mock private SmsCouponProductRelationMapper productRelationMapper;
    @Mock private SmsCouponProductCategoryRelationMapper productCategoryRelationMapper;
    @Mock private SmsCouponProductRelationDao productRelationDao;
    @Mock private SmsCouponProductCategoryRelationDao productCategoryRelationDao;
    @Mock private SmsCouponDao couponDao;

    @InjectMocks private SmsCouponServiceImpl couponService;

    @Test
    @DisplayName("create_全场券_只写券表不写商品关联")
    void create_全场券_只写券表不写商品关联() {
        SmsCouponParam param = param(0, null, null);
        stubInsertBackfillId();

        int count = couponService.create(param);

        assertThat(count).isEqualTo(1);
        // 发布时 count 初始化为 publishCount，领取/使用计数归零
        assertThat(param.getCount()).isEqualTo(param.getPublishCount());
        assertThat(param.getUseCount()).isZero();
        assertThat(param.getReceiveCount()).isZero();
        verify(productRelationDao, never()).insertList(anyList());
        verify(productCategoryRelationDao, never()).insertList(anyList());
    }

    @Test
    @DisplayName("create_指定商品券_写券表与商品关联表")
    void create_指定商品券_写券表与商品关联表() {
        SmsCouponProductRelation relation = new SmsCouponProductRelation();
        relation.setProductId(100L);
        SmsCouponParam param = param(2, List.of(relation), null);
        stubInsertBackfillId();

        couponService.create(param);

        ArgumentCaptor<List<SmsCouponProductRelation>> captor = ArgumentCaptor.forClass(List.class);
        verify(productRelationDao).insertList(captor.capture());
        // 券 id 必须回填，否则关联记录挂不到任何券上
        assertThat(captor.getValue()).allSatisfy(r -> assertThat(r.getCouponId()).isEqualTo(88L));
        verify(productCategoryRelationDao, never()).insertList(anyList());
    }

    @Test
    @DisplayName("create_指定分类券_写券表与分类关联表")
    void create_指定分类券_写券表与分类关联表() {
        SmsCouponProductCategoryRelation relation = new SmsCouponProductCategoryRelation();
        relation.setProductCategoryId(3L);
        SmsCouponParam param = param(1, null, List.of(relation));
        stubInsertBackfillId();

        couponService.create(param);

        ArgumentCaptor<List<SmsCouponProductCategoryRelation>> captor = ArgumentCaptor.forClass(List.class);
        verify(productCategoryRelationDao).insertList(captor.capture());
        assertThat(captor.getValue()).allSatisfy(r -> assertThat(r.getCouponId()).isEqualTo(88L));
        verify(productRelationDao, never()).insertList(anyList());
    }

    @Test
    @DisplayName("delete_删除券_同时清理商品与分类关联")
    void delete_删除券_同时清理商品与分类关联() {
        couponService.delete(88L);

        verify(couponMapper).deleteByPrimaryKey(88L);
        verify(productRelationMapper).deleteByExample(any(SmsCouponProductRelationExample.class));
        verify(productCategoryRelationMapper)
                .deleteByExample(any(SmsCouponProductCategoryRelationExample.class));
    }

    // ==================== 夹具 ====================

    private void stubInsertBackfillId() {
        doAnswer(inv -> {
            SmsCoupon inserted = inv.getArgument(0);
            inserted.setId(88L);
            return 1;
        }).when(couponMapper).insert(any(SmsCoupon.class));
    }

    private SmsCouponParam param(int useType,
                                 List<SmsCouponProductRelation> productRelations,
                                 List<SmsCouponProductCategoryRelation> categoryRelations) {
        SmsCouponParam param = new SmsCouponParam();
        param.setName("测试券");
        param.setType(0);
        param.setUseType(useType);
        param.setAmount(new BigDecimal("10"));
        param.setMinPoint(new BigDecimal("100"));
        param.setPublishCount(100);
        param.setCount(100);
        param.setUseCount(0);
        param.setReceiveCount(0);
        param.setProductRelationList(productRelations);
        param.setProductCategoryRelationList(categoryRelations);
        return param;
    }
}
