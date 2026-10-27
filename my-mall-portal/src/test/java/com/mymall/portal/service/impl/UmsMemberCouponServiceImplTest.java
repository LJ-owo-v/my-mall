package com.mymall.portal.service.impl;

import com.mymall.common.exception.BizException;
import com.mymall.portal.dao.*;
import com.mymall.portal.domain.CartPromotionItem;
import com.mymall.portal.domain.SmsCouponHistoryDetail;
import com.mymall.portal.model.*;
import com.mymall.portal.service.UmsMemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UmsMemberCouponServiceImpl 纯单元测试。
 *
 * <p>领券的"限领"与"库存"两道闸门都在 SQL 条件更新里（影响行数裁决），
 * 服务层只根据返回值决定抛异常 —— 这正是单测能覆盖的部分。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UmsMemberCouponServiceImpl")
class UmsMemberCouponServiceImplTest {

    @Mock private UmsMemberService memberService;
    @Mock private SmsCouponMapper couponMapper;
    @Mock private SmsCouponHistoryMapper couponHistoryMapper;
    @Mock private SmsCouponHistoryDao couponHistoryDao;
    @Mock private SmsCouponProductRelationMapper couponProductRelationMapper;
    @Mock private SmsCouponProductCategoryRelationMapper couponProductCategoryRelationMapper;
    @Mock private PmsProductMapper productMapper;

    @InjectMocks private UmsMemberCouponServiceImpl couponService;

    @Test
    @DisplayName("add_该券已领取过_拒绝领取")
    void add_该券已领取过_拒绝领取() {
        when(memberService.getCurrentMember()).thenReturn(member());
        when(couponMapper.selectByPrimaryKey(5L)).thenReturn(coupon(5L, 100));
        when(couponMapper.decreaseCount(5L)).thenReturn(1);
        // 条件插入的影响行数为 0：已领过（或已到 per_limit）
        when(couponHistoryMapper.insertIfUnderLimit(any(SmsCouponHistory.class))).thenReturn(0);

        assertThatThrownBy(() -> couponService.add(5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已经领取过");
        // 抛异常 → 外层事务回滚，第 ⑤ 步的库存扣减会被撤销，不能出现"券没领到但库存少了"
        verify(couponMapper).decreaseCount(5L);
    }

    @Test
    @DisplayName("add_已达每人限领数量_拒绝领取")
    void add_已达每人限领数量_拒绝领取() {
        when(memberService.getCurrentMember()).thenReturn(member());
        SmsCoupon coupon = coupon(5L, 100);
        coupon.setPerLimit(1);              // 每人限领 1 张
        when(couponMapper.selectByPrimaryKey(5L)).thenReturn(coupon);
        when(couponMapper.decreaseCount(5L)).thenReturn(1);
        when(couponHistoryMapper.insertIfUnderLimit(any(SmsCouponHistory.class))).thenReturn(0);

        // 缺陷 #8 的修复方式：限领判断被下沉进 INSERT ... WHERE (SELECT COUNT(*) ...) < per_limit，
        // 服务层拿不到"已领几张"，只能看影响行数 —— 因此与"已领过"收敛为同一条分支
        assertThatThrownBy(() -> couponService.add(5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已经领取过");
    }

    @Test
    @DisplayName("add_券库存不足_原子扣减影响0时拒绝")
    void add_券库存不足_原子扣减影响0时拒绝() {
        when(memberService.getCurrentMember()).thenReturn(member());
        when(couponMapper.selectByPrimaryKey(5L)).thenReturn(coupon(5L, 100));
        // 缺陷 #7：预检 count>0 通过，但并发下已被抢完 → 原子扣减返回 0
        when(couponMapper.decreaseCount(5L)).thenReturn(0);

        assertThatThrownBy(() -> couponService.add(5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已被抢完");
        verify(couponHistoryMapper, never()).insertIfUnderLimit(any(SmsCouponHistory.class));
    }

    @Test
    @DisplayName("add_正常领取_写领取记录且券码唯一")
    void add_正常领取_写领取记录且券码唯一() {
        when(memberService.getCurrentMember()).thenReturn(member());
        when(couponMapper.selectByPrimaryKey(5L)).thenReturn(coupon(5L, 100));
        when(couponMapper.decreaseCount(5L)).thenReturn(1);
        when(couponHistoryMapper.insertIfUnderLimit(any(SmsCouponHistory.class))).thenReturn(1);

        couponService.add(5L);

        ArgumentCaptor<SmsCouponHistory> captor = ArgumentCaptor.forClass(SmsCouponHistory.class);
        verify(couponHistoryMapper).insertIfUnderLimit(captor.capture());
        SmsCouponHistory saved = captor.getValue();

        assertThat(saved.getCouponId()).isEqualTo(5L);
        assertThat(saved.getMemberId()).isEqualTo(1L);
        assertThat(saved.getGetType()).isEqualTo(1);      // 1=主动领取
        assertThat(saved.getUseStatus()).isZero();        // 0=未使用
        // 券码 = 8 位时间戳 + 4 位随机 + 4 位会员号，共 16 位数字
        assertThat(saved.getCouponCode()).matches("\\d{16}").endsWith("0001");
        verify(couponMapper).decreaseCount(5L);
    }

    @Test
    @DisplayName("listCart_未达门槛的券被过滤到不可用")
    void listCart_未达门槛的券被过滤到不可用() {
        when(memberService.getCurrentMember()).thenReturn(member());
        // 购物车实付 100 元
        List<CartPromotionItem> cart = List.of(cartItem("100", "0", 1));
        SmsCouponHistoryDetail enabled = detail(11L, "50", null, future());

        when(couponHistoryDao.getDetailList(1L, null)).thenReturn(List.of(
                enabled,
                detail(12L, "200", null, future())));  // 满 200 → 不可用

        List<SmsCouponHistoryDetail> enableList = couponService.listCart(cart, 1);
        List<SmsCouponHistoryDetail> disableList = couponService.listCart(cart, 0);

        assertThat(enableList).extracting(d -> d.getCoupon().getId()).containsExactly(11L);
        assertThat(disableList).extracting(d -> d.getCoupon().getId()).containsExactly(12L);
    }

    @Test
    @DisplayName("listCart_过期券不出现在可用列表")
    void listCart_过期券不出现在可用列表() {
        when(memberService.getCurrentMember()).thenReturn(member());
        List<CartPromotionItem> cart = List.of(cartItem("100", "0", 1));
        // 相对时间：昨天已过期
        Date yesterday = new Date(System.currentTimeMillis() - 24 * 3600 * 1000L);
        when(couponHistoryDao.getDetailList(1L, null))
                .thenReturn(List.of(detail(13L, "50", null, yesterday)));

        List<SmsCouponHistoryDetail> enableList = couponService.listCart(cart, 1);

        assertThat(enableList).isEmpty();
    }

    @Test
    @DisplayName("listCart_实付金额差1元内_当前实现因intValue截断误判为可用")
    void listCart_实付金额差1元内_当前实现因intValue截断误判为可用() {
        when(memberService.getCurrentMember()).thenReturn(member());
        // 实付 99.9 元，券门槛 100 元：差额 -0.1
        List<CartPromotionItem> cart = List.of(cartItem("99.9", "0", 1));
        when(couponHistoryDao.getDetailList(1L, null))
                .thenReturn(List.of(detail(14L, "100", null, future())));

        List<SmsCouponHistoryDetail> enableList = couponService.listCart(cart, 1);

        // 如实记录当前行为：totalAmount.subtract(minPoint).intValue() 把 -0.1 截断成 0，
        // 于是"差 1 元以内"的券被判定为可用（详见回报"疑似缺陷"第 2 条，未改业务代码）
        assertThat(enableList).extracting(d -> d.getCoupon().getId()).containsExactly(14L);
    }

    // ==================== 夹具 ====================

    private UmsMember member() {
        UmsMember member = new UmsMember();
        member.setId(1L);
        member.setNickname("李京");
        member.setUsername("lijing");
        return member;
    }

    private SmsCoupon coupon(Long id, int count) {
        SmsCoupon coupon = new SmsCoupon();
        coupon.setId(id);
        coupon.setCount(count);
        coupon.setEnableTime(new Date(System.currentTimeMillis() - 3600 * 1000L)); // 1 小时前可领
        coupon.setEndTime(future());
        coupon.setUseType(0);
        coupon.setAmount(new BigDecimal("10"));
        coupon.setMinPoint(new BigDecimal("50"));
        return coupon;
    }

    private SmsCouponHistoryDetail detail(Long couponId, String minPoint, Integer useType, Date endTime) {
        SmsCoupon coupon = new SmsCoupon();
        coupon.setId(couponId);
        coupon.setMinPoint(new BigDecimal(minPoint));
        coupon.setUseType(useType == null ? 0 : useType);
        coupon.setAmount(new BigDecimal("10"));
        coupon.setEndTime(endTime);

        SmsCouponHistoryDetail detail = new SmsCouponHistoryDetail();
        detail.setCouponId(couponId);
        detail.setMemberId(1L);
        detail.setCoupon(coupon);
        detail.setCategoryRelationList(new ArrayList<>());
        detail.setProductRelationList(new ArrayList<>());
        return detail;
    }

    private CartPromotionItem cartItem(String price, String reduce, int quantity) {
        CartPromotionItem item = new CartPromotionItem();
        item.setPrice(new BigDecimal(price));
        item.setReduceAmount(new BigDecimal(reduce));
        item.setQuantity(quantity);
        item.setProductId(100L);
        item.setProductCategoryId(3L);
        return item;
    }

    private Date future() {
        return new Date(System.currentTimeMillis() + 7L * 24 * 3600 * 1000);
    }
}
