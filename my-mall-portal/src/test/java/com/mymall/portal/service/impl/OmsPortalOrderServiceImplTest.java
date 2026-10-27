package com.mymall.portal.service.impl;

import com.mymall.common.exception.BizException;
import com.mymall.common.redis.RedisService;
import com.mymall.portal.component.CancelOrderSender;
import com.mymall.portal.dao.*;
import com.mymall.portal.domain.*;
import com.mymall.portal.model.*;
import com.mymall.portal.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * OmsPortalOrderServiceImpl 纯单元测试（不启动 Spring 容器）。
 *
 * <p>覆盖下单、支付回调、取消、对账四类核心路径，重点验证并发一致性与幂等裁决：
 * 影响行数为 0 时"谁有权动库存/积分"必须由 DB 条件更新的返回值决定，而不是应用层 if。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OmsPortalOrderServiceImpl")
class OmsPortalOrderServiceImplTest {

    @Mock private UmsMemberService memberService;
    @Mock private OmsCartItemService cartItemService;
    @Mock private UmsMemberReceiveAddressService memberReceiveAddressService;
    @Mock private UmsMemberCouponService memberCouponService;
    @Mock private UmsIntegrationConsumeSettingMapper integrationConsumeSettingMapper;
    @Mock private PmsSkuStockMapper skuStockMapper;
    @Mock private RedisService redisService;
    @Mock private UmsMemberMapper memberMapper;
    @Mock private UmsMemberCacheService memberCacheService;
    @Mock private OmsOrderSettingMapper orderSettingMapper;
    @Mock private OmsOrderMapper orderMapper;
    @Mock private PortalOrderItemDao orderItemDao;
    @Mock private SmsCouponHistoryMapper couponHistoryMapper;
    @Mock private CancelOrderSender cancelOrderSender;
    @Mock private PortalOrderDao portalOrderDao;
    @Mock private OmsOrderItemMapper orderItemMapper;

    @InjectMocks private OmsPortalOrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        // @Value 字段在纯单测里不会被注入，补上以便断言 Redis key 的拼装规则
        ReflectionTestUtils.setField(orderService, "REDIS_DATABASE", "mall");
        ReflectionTestUtils.setField(orderService, "REDIS_KEY_ORDER_ID", "order:id:");
    }

    // ==================== 订单号生成 ====================

    @Test
    @DisplayName("generateOrderSn_格式为18位纯数字")
    void generateOrderSn_格式为18位纯数字() {
        OmsOrder order = placeOrderHappyPath().getOrder();

        // 8 位日期 + 2 位平台号 + 2 位支付方式 + 6 位自增
        assertThat(order.getOrderSn()).matches("\\d{18}");
    }

    @Test
    @DisplayName("generateOrderSn_前8位为当天日期")
    void generateOrderSn_前8位为当天日期() {
        OmsOrder order = placeOrderHappyPath().getOrder();

        // 不硬编码日期：只断言"前 8 位 == 今天"，跨天运行也不会红
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        assertThat(order.getOrderSn().substring(0, 8)).isEqualTo(today);
    }

    @Test
    @DisplayName("generateOrderSn_调用Redis自增且key按天分桶")
    void generateOrderSn_调用Redis自增且key按天分桶() {
        placeOrderHappyPath();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisService).incr(keyCaptor.capture(), eq(1L));
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        // key 必须带日期，否则跨天自增不归零、单号会越来越长
        assertThat(keyCaptor.getValue()).contains(today).isEqualTo("mall:order:id:" + today);
    }

    // ==================== 下单主流程 ====================

    @Test
    @DisplayName("generateOrder_未选商品时_抛出异常")
    void generateOrder_未选商品时_抛出异常() {
        OrderParam param = baseParam();
        param.setCartIds(new ArrayList<>());
        when(memberService.getCurrentMember()).thenReturn(member(1000));
        when(memberService.getById(1L)).thenReturn(member(1000));

        assertThatThrownBy(() -> orderService.generateOrder(param))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请选择要结算的商品");
        // 购物车为空时绝不能走到写订单
        verify(orderMapper, never()).insert(any(OmsOrder.class));
    }

    @Test
    @DisplayName("generateOrder_库存锁定影响行数为0时_抛出库存不足")
    void generateOrder_库存锁定影响行数为0时_抛出库存不足() {
        OrderParam param = baseParam();
        stubBeforeStock(param);
        // hasStock 预检查通过（realStock=10 够 2 件），但 DB 原子锁库存失败
        when(portalOrderDao.lockStockSafely(anyLong(), anyInt())).thenReturn(0);

        assertThatThrownBy(() -> orderService.generateOrder(param))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("库存不足");
        verify(orderMapper, never()).insert(any(OmsOrder.class));
    }

    @Test
    @DisplayName("generateOrder_正常下单_写入订单并锁定库存")
    void generateOrder_正常下单_写入订单并锁定库存() {
        PlacedOrderResult result = placeOrderHappyPath();
        OmsOrder order = result.getOrder();

        // 金额：原价 100×2 = 200，无促销/券/积分
        assertThat(order.getTotalAmount()).isEqualByComparingTo("200");
        assertThat(order.getPayAmount()).isEqualByComparingTo("200");
        assertThat(order.getStatus()).isZero();
        assertThat(order.getDeleteStatus()).isZero();
        // 订单明细回填了订单 id 与订单号（否则明细会挂在 null 订单上）
        assertThat(result.getItems()).allSatisfy(item -> {
            assertThat(item.getOrderId()).isEqualTo(100L);
            assertThat(item.getOrderSn()).isEqualTo(order.getOrderSn());
        });

        verify(orderMapper).insert(any(OmsOrder.class));
        verify(portalOrderDao).lockStockSafely(Long.valueOf(888L), 2);
        verify(orderItemDao).insertList(anyList());
        // 购物车清理与延迟消息在最后一步，缺一个就会留下"已下单但购物车还在 / 永不过期"
        verify(cartItemService).delete(eq(1L), anyList());
        verify(cancelOrderSender).sendMessage(eq(100L), anyLong());
    }

    @Test
    @DisplayName("generateOrder_购物车抢占行数不足时_拒绝重复提交")
    void generateOrder_购物车抢占行数不足时_拒绝重复提交() {
        OrderParam param = baseParam();
        when(memberService.getCurrentMember()).thenReturn(member(1000));
        when(memberService.getById(1L)).thenReturn(member(1000));
        when(cartItemService.listPromotion(eq(1L), anyList()))
                .thenReturn(List.of(cartItem(10L, 100L, 888L, "100", 2, 10)));
        // 请求 1 行，实际只抢到 0 行 → 已被并发请求消费
        when(portalOrderDao.occupyCartItems(eq(1L), anyList())).thenReturn(0);

        assertThatThrownBy(() -> orderService.generateOrder(param))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请勿重复提交");
        // 抢占失败必须在锁库存之前退出，不能产生订单
        verify(portalOrderDao, never()).lockStockSafely(anyLong(), anyInt());
        verify(orderMapper, never()).insert(any(OmsOrder.class));
    }

    // ==================== 支付回调 ====================

    @Test
    @DisplayName("paySuccess_订单非待付款时_不重复扣减库存")
    void paySuccess_订单非待付款时_不重复扣减库存() {
        // 条件更新影响行数 0：订单已支付或已关闭 → 本次调用无权处理
        when(portalOrderDao.payOrderIfPending(1L, 1)).thenReturn(0);

        Integer count = orderService.paySuccess(1L, 1);

        assertThat(count).isZero();
        verify(portalOrderDao, never()).getDetail(anyLong());
        verify(portalOrderDao, never()).paySkuStockSafely(anyLong(), anyInt());
    }

    @Test
    @DisplayName("paySuccessByOrderSn_重复回调时_按订单号入口同样幂等")
    void paySuccessByOrderSn_重复回调时_按订单号入口同样幂等() {
        when(orderMapper.selectByExample(any(OmsOrderExample.class)))
                .thenReturn(List.of(paidOrder(1L, 0)));
        when(portalOrderDao.payOrderIfPending(1L, 1)).thenReturn(0);

        orderService.paySuccessByOrderSn("SN202610270001", 1);

        verify(portalOrderDao, never()).paySkuStockSafely(anyLong(), anyInt());
    }

    // ==================== 金额校验 ====================

    @Test
    @DisplayName("checkOrderAmount_金额一致时_返回true并忽略scale差异")
    void checkOrderAmount_金额一致时_返回true并忽略scale差异() {
        when(orderMapper.selectByExample(any(OmsOrderExample.class)))
                .thenReturn(List.of(paidOrder(1L, 0)));

        // 订单存的是 3699.00，回调给 3699.0 —— BigDecimal.equals 会判不等，compareTo 才正确
        assertThat(orderService.checkOrderAmount("SN202610270001", "3699.0")).isTrue();
    }

    @Test
    @DisplayName("checkOrderAmount_金额不符时_返回false")
    void checkOrderAmount_金额不符时_返回false() {
        when(orderMapper.selectByExample(any(OmsOrderExample.class)))
                .thenReturn(List.of(paidOrder(1L, 0)));

        // 用 0.01 元结掉 3699 元订单的资损路径必须被拦住
        assertThat(orderService.checkOrderAmount("SN202610270001", "0.01")).isFalse();
    }

    // ==================== 取消订单 ====================

    @Test
    @DisplayName("cancelOrder_重复取消时_不重复释放库存")
    void cancelOrder_重复取消时_不重复释放库存() {
        when(orderMapper.selectByExample(any(OmsOrderExample.class)))
                .thenReturn(List.of(paidOrder(9L, 0)));
        // 已被其他路径关闭 → 本次无权释放
        when(portalOrderDao.updateOrderStatusByIdIfPending(9L, 4)).thenReturn(0);

        orderService.cancelOrder(9L);

        verify(portalOrderDao, never()).releaseSkuStockLockSafely(anyLong(), anyInt());
        verify(orderItemMapper, never()).selectByExample(any(OmsOrderItemExample.class));
    }

    @Test
    @DisplayName("cancelTimeOutOrder_无明细订单也能被取消")
    void cancelTimeOutOrder_无明细订单也能被取消() {
        OmsOrderSetting setting = new OmsOrderSetting();
        setting.setNormalOrderOvertime(30);
        when(orderSettingMapper.selectByPrimaryKey(1L)).thenReturn(setting);

        // 缺陷 #10 的场景：订单存在但没有明细行
        OmsOrderDetail noItemOrder = new OmsOrderDetail();
        noItemOrder.setId(7L);
        noItemOrder.setMemberId(1L);
        noItemOrder.setOrderItemList(null);
        when(portalOrderDao.getTimeOutOrders(30)).thenReturn(List.of(noItemOrder));
        when(portalOrderDao.updateOrderStatusByIdIfPending(7L, 4)).thenReturn(1);

        Integer processed = orderService.cancelTimeOutOrder();

        // 关键：不能因为明细为空就跳过该订单（跳过会让订单永远停在待付款）
        assertThat(processed).isEqualTo(1);
        verify(portalOrderDao).updateOrderStatusByIdIfPending(7L, 4);
        verify(portalOrderDao, never()).releaseSkuStockLockSafely(anyLong(), anyInt());
    }

    // ==================== 对账 ====================

    @Test
    @DisplayName("reconcile_库存不守恒时_返回不一致结果")
    void reconcile_库存不守恒时_返回不一致结果() {
        // DAO 返回的是"差异明细行"，命中数由明细条数聚合得出（不是行内 hitCount 字段）
        List<ReconcileResult> violatedRows = List.of(
                new ReconcileResult("I1", "库存守恒", 1, "SKU 201: lock_stock=3 但待付款明细合计=1"),
                new ReconcileResult("I1", "库存守恒", 1, "SKU 205: lock_stock=2 但待付款明细合计=0"));
        when(portalOrderDao.reconcileStockConservation()).thenReturn(violatedRows);
        when(portalOrderDao.reconcileNegativeStock()).thenReturn(Collections.emptyList());
        when(portalOrderDao.reconcileLockOverStock()).thenReturn(Collections.emptyList());
        when(portalOrderDao.reconcilePayStatusTime()).thenReturn(Collections.emptyList());
        when(portalOrderDao.reconcileOrphanLock()).thenReturn(Collections.emptyList());

        List<ReconcileResult> results = orderService.reconcile();

        assertThat(results).hasSize(5);
        ReconcileResult i1 = results.get(0);
        assertThat(i1.getCode()).isEqualTo("I1");
        assertThat(i1.getHitCount()).isEqualTo(2);
        assertThat(i1.getVerdict()).isEqualTo("FAIL");
        assertThat(i1.isPassed()).isFalse();
        assertThat(i1.getDetail()).contains("lock_stock");
    }

    @Test
    @DisplayName("reconcile_数据一致时_全部PASS且明细为空")
    void reconcile_数据一致时_全部PASS且明细为空() {
        when(portalOrderDao.reconcileStockConservation()).thenReturn(Collections.emptyList());
        when(portalOrderDao.reconcileNegativeStock()).thenReturn(Collections.emptyList());
        when(portalOrderDao.reconcileLockOverStock()).thenReturn(Collections.emptyList());
        when(portalOrderDao.reconcilePayStatusTime()).thenReturn(Collections.emptyList());
        when(portalOrderDao.reconcileOrphanLock()).thenReturn(Collections.emptyList());

        List<ReconcileResult> results = orderService.reconcile();

        assertThat(results).hasSize(5).allSatisfy(r -> {
            assertThat(r.isPassed()).isTrue();
            assertThat(r.getHitCount()).isZero();
            assertThat(r.getDetail()).isEmpty();
        });
        assertThat(results).extracting(ReconcileResult::getCode)
                .containsExactly("I1", "I2", "I3", "I4", "I5");
    }

    // ==================== 补充：优惠券与积分分支 ====================

    @Test
    @DisplayName("generateOrder_使用全场券时_按价格比例分摊且订单券金额等于面额")
    void generateOrder_使用全场券时_按价格比例分摊且订单券金额等于面额() {
        OrderParam param = baseParam();
        param.setCouponId(5L);
        stubBeforeStock(param);
        when(portalOrderDao.lockStockSafely(anyLong(), anyInt())).thenReturn(1);
        stubAfterStock();

        SmsCoupon coupon = new SmsCoupon();
        coupon.setId(5L);
        coupon.setAmount(new BigDecimal("100"));
        coupon.setUseType(0);
        SmsCouponHistoryDetail detail = new SmsCouponHistoryDetail();
        detail.setCoupon(coupon);
        when(memberCouponService.listCart(anyList(), eq(1))).thenReturn(List.of(detail));
        when(couponHistoryMapper.useCouponIfUnused(5L, 1L)).thenReturn(1);

        OmsOrder order = (OmsOrder) orderService.generateOrder(param).get("order");

        // 200 元的单子只有 1 行，全场券 100 元应全额落在该行
        assertThat(order.getCouponAmount()).isEqualByComparingTo("100");
        assertThat(order.getPayAmount()).isEqualByComparingTo("100");
        assertThat(order.getCouponId()).isEqualTo(5L);
        verify(couponHistoryMapper).useCouponIfUnused(5L, 1L);
    }

    @Test
    @DisplayName("generateOrder_使用积分时_按规则折抵并原子扣减积分")
    void generateOrder_使用积分时_按规则折抵并原子扣减积分() {
        OrderParam param = baseParam();
        param.setUseIntegration(100);
        stubBeforeStock(param);
        when(portalOrderDao.lockStockSafely(anyLong(), anyInt())).thenReturn(1);
        stubAfterStock();

        UmsIntegrationConsumeSetting setting = new UmsIntegrationConsumeSetting();
        setting.setUseUnit(100);            // 100 积分抵 1 元
        setting.setMaxPercentPerOrder(50);  // 最多抵订单 50%
        setting.setCouponStatus(0);
        when(integrationConsumeSettingMapper.selectByPrimaryKey(1L)).thenReturn(setting);
        when(memberMapper.deductIntegrationSafely(1L, 100)).thenReturn(1);

        OmsOrder order = (OmsOrder) orderService.generateOrder(param).get("order");

        assertThat(order.getIntegrationAmount()).isEqualByComparingTo("1");
        assertThat(order.getUseIntegration()).isEqualTo(100);
        assertThat(order.getPayAmount()).isEqualByComparingTo("199");
        // 扣减靠 DB 条件更新，同时要清缓存（否则下次仍读到旧积分）
        verify(memberMapper).deductIntegrationSafely(1L, 100);
        verify(memberCacheService).delMember(1L);
    }

    // ==================== 测试夹具 ====================

    /** 下单结果（order + orderItemList 的强类型包装，避免到处强转 Map） */
    private static final class PlacedOrderResult {
        private final OmsOrder order;
        private final List<OmsOrderItem> items;

        private PlacedOrderResult(OmsOrder order, List<OmsOrderItem> items) {
            this.order = order;
            this.items = items;
        }

        OmsOrder getOrder() {
            return order;
        }

        List<OmsOrderItem> getItems() {
            return items;
        }
    }

    /** 走一遍"无券无积分"的正常下单，返回订单与明细 */
    private PlacedOrderResult placeOrderHappyPath() {
        OrderParam param = baseParam();
        stubBeforeStock(param);
        when(portalOrderDao.lockStockSafely(anyLong(), anyInt())).thenReturn(1);
        stubAfterStock();

        Map<String, Object> raw = orderService.generateOrder(param);
        return new PlacedOrderResult((OmsOrder) raw.get("order"), castItems(raw.get("orderItemList")));
    }

    @SuppressWarnings("unchecked")
    private List<OmsOrderItem> castItems(Object raw) {
        return (List<OmsOrderItem>) raw;
    }

    /** 下单前半段（校验地址 / 会员 / 购物车 / 抢占）的公共桩 */
    private void stubBeforeStock(OrderParam param) {
        when(memberService.getCurrentMember()).thenReturn(member(1000));
        when(memberService.getById(1L)).thenReturn(member(1000));
        List<CartPromotionItem> cart = List.of(cartItem(10L, 100L, 888L, "100", 2, 10));
        when(cartItemService.listPromotion(eq(1L), eq(param.getCartIds()))).thenReturn(cart);
        when(portalOrderDao.occupyCartItems(eq(1L), eq(param.getCartIds())))
                .thenReturn(param.getCartIds().size());
    }

    /** 下单后半段（订单号 / 写库 / 明细 / 清理）的公共桩 */
    private void stubAfterStock() {
        when(memberReceiveAddressService.getItem(1L)).thenReturn(address());
        when(redisService.incr(anyString(), eq(1L))).thenReturn(1L);
        OmsOrderSetting setting = new OmsOrderSetting();
        setting.setConfirmOvertime(15);
        setting.setNormalOrderOvertime(30);   // sendDelayMessageCancelOrder 会用它算延迟时长
        when(orderSettingMapper.selectByExample(any(OmsOrderSettingExample.class)))
                .thenReturn(List.of(setting));
        when(orderSettingMapper.selectByPrimaryKey(1L)).thenReturn(setting);
        // 模拟 MBG 回填自增主键
        doAnswer(inv -> {
            OmsOrder inserted = inv.getArgument(0);
            inserted.setId(100L);
            return 1;
        }).when(orderMapper).insert(any(OmsOrder.class));
        // orderItemDao.insertList 是 void：不需要打桩，直接 verify
        when(cartItemService.delete(eq(1L), anyList())).thenReturn(1);
    }

    private OrderParam baseParam() {
        OrderParam param = new OrderParam();
        param.setMemberReceiveAddressId(1L);
        param.setPayType(1);
        param.setCartIds(new ArrayList<>(List.of(10L)));
        return param;
    }

    private UmsMember member(int integration) {
        UmsMember member = new UmsMember();
        member.setId(1L);
        member.setUsername("lijing");
        member.setNickname("李京");
        member.setIntegration(integration);
        return member;
    }

    private CartPromotionItem cartItem(Long cartId, Long productId, Long skuId,
                                       String price, int quantity, int realStock) {
        CartPromotionItem item = new CartPromotionItem();
        item.setId(cartId);
        item.setProductId(productId);
        item.setProductSkuId(skuId);
        item.setProductName("测试商品");
        item.setProductSkuCode("SKU-888");
        item.setProductCategoryId(3L);
        item.setPrice(new BigDecimal(price));
        item.setQuantity(quantity);
        item.setReduceAmount(BigDecimal.ZERO);
        item.setRealStock(realStock);
        item.setIntegration(0);
        item.setGrowth(0);
        item.setPromotionMessage("无优惠");
        return item;
    }

    private UmsMemberReceiveAddress address() {
        UmsMemberReceiveAddress address = new UmsMemberReceiveAddress();
        address.setId(1L);
        address.setMemberId(1L);
        address.setName("李京");
        address.setPhoneNumber("13642567329");
        address.setPostCode("510000");
        address.setProvince("广东省");
        address.setCity("广州市");
        address.setRegion("白云区");
        address.setDetailAddress("白云大道北 2 号");
        return address;
    }

    private OmsOrder paidOrder(Long id, int status) {
        OmsOrder order = new OmsOrder();
        order.setId(id);
        order.setOrderSn("SN202610270001");
        order.setMemberId(1L);
        order.setStatus(status);
        order.setDeleteStatus(0);
        order.setPayAmount(new BigDecimal("3699.00"));
        order.setCreateTime(new Date());
        return order;
    }
}
