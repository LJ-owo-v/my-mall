package com.mymall.portal.service.impl;

import com.mymall.common.exception.BizException;
import com.mymall.portal.dao.OmsCartItemMapper;
import com.mymall.portal.dao.PmsProductMapper;
import com.mymall.portal.dao.PmsSkuStockMapper;
import com.mymall.portal.dao.PortalProductDao;
import com.mymall.portal.model.OmsCartItem;
import com.mymall.portal.model.OmsCartItemExample;
import com.mymall.portal.model.PmsProduct;
import com.mymall.portal.model.PmsSkuStock;
import com.mymall.portal.model.UmsMember;
import com.mymall.portal.service.OmsPromotionService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OmsCartItemServiceImpl 纯单元测试。
 *
 * <p>核心是"服务端定价"：加购只认 skuId，价格/商品信息一律回查覆盖，前端传什么都不算数。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OmsCartItemServiceImpl")
class OmsCartItemServiceImplTest {

    @Mock private OmsCartItemMapper cartItemMapper;
    @Mock private UmsMemberService memberService;
    @Mock private OmsPromotionService promotionService;
    @Mock private PortalProductDao productDao;
    @Mock private PmsSkuStockMapper skuStockMapper;
    @Mock private PmsProductMapper productMapper;

    @InjectMocks private OmsCartItemServiceImpl cartItemService;

    @Test
    @DisplayName("add_前端传0.01元但SKU真实价99_落库必须是99")
    void add_前端传0_01元但SKU真实价99_落库必须是99() {
        when(memberService.getCurrentMember()).thenReturn(member());
        when(skuStockMapper.selectByPrimaryKey(9001L)).thenReturn(sku(9001L, 100L, "99"));
        when(productMapper.selectByPrimaryKey(100L)).thenReturn(product(100L));
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(new ArrayList<>());
        when(cartItemMapper.insert(any(OmsCartItem.class))).thenReturn(1);

        OmsCartItem request = new OmsCartItem();
        request.setProductSkuId(9001L);
        request.setQuantity(1);
        request.setPrice(new BigDecimal("0.01"));    // 客户端伪造的价格
        request.setProductId(null);                  // 客户端甚至可以什么都不传

        int count = cartItemService.add(request);

        ArgumentCaptor<OmsCartItem> captor = ArgumentCaptor.forClass(OmsCartItem.class);
        verify(cartItemMapper).insert(captor.capture());
        OmsCartItem saved = captor.getValue();

        assertThat(count).isEqualTo(1);
        // 缺陷 #20：价格必须来自服务端，0.01 绝不能落库
        assertThat(saved.getPrice()).isEqualByComparingTo("99");
        assertThat(saved.getProductId()).isEqualTo(100L);
        assertThat(saved.getProductName()).isEqualTo("测试商品");
        assertThat(saved.getMemberId()).isEqualTo(1L);
        assertThat(saved.getDeleteStatus()).isZero();
    }

    @Test
    @DisplayName("add_SKU不存在_抛出异常且不写库")
    void add_SKU不存在_抛出异常且不写库() {
        when(memberService.getCurrentMember()).thenReturn(member());
        when(skuStockMapper.selectByPrimaryKey(9001L)).thenReturn(null);

        OmsCartItem request = new OmsCartItem();
        request.setProductSkuId(9001L);
        request.setQuantity(1);

        assertThatThrownBy(() -> cartItemService.add(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("规格不存在");
        verify(cartItemMapper, never()).insert(any(OmsCartItem.class));
    }

    @Test
    @DisplayName("add_同SKU已在购物车_数量累加而不是新增行")
    void add_同SKU已在购物车_数量累加而不是新增行() {
        when(memberService.getCurrentMember()).thenReturn(member());
        when(skuStockMapper.selectByPrimaryKey(9001L)).thenReturn(sku(9001L, 100L, "99"));
        when(productMapper.selectByPrimaryKey(100L)).thenReturn(product(100L));

        OmsCartItem exist = new OmsCartItem();
        exist.setId(77L);
        exist.setProductSkuId(9001L);
        exist.setQuantity(2);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(List.of(exist));
        when(cartItemMapper.updateByPrimaryKey(any(OmsCartItem.class))).thenReturn(1);

        OmsCartItem request = new OmsCartItem();
        request.setProductSkuId(9001L);
        request.setQuantity(3);

        int count = cartItemService.add(request);

        assertThat(count).isEqualTo(1);
        // 累加后 2 + 3 = 5，且走的是 update 不是 insert（否则同一 SKU 会出现两行）
        assertThat(exist.getQuantity()).isEqualTo(5);
        verify(cartItemMapper).updateByPrimaryKey(exist);
        verify(cartItemMapper, never()).insert(any(OmsCartItem.class));
    }

    @Test
    @DisplayName("updateQuantity_数量为负数_当前实现未做校验直接落库")
    void updateQuantity_数量为负数_当前实现未做校验直接落库() {
        when(cartItemMapper.updateByExampleSelective(any(OmsCartItem.class), any(OmsCartItemExample.class)))
                .thenReturn(1);

        int count = cartItemService.updateQuantity(77L, 1L, -5);

        ArgumentCaptor<OmsCartItem> captor = ArgumentCaptor.forClass(OmsCartItem.class);
        verify(cartItemMapper).updateByExampleSelective(captor.capture(), any(OmsCartItemExample.class));

        // 如实记录当前行为：服务层没有 quantity>0 的守卫，-5 会被原样写进 oms_cart_item
        // （详见回报"疑似缺陷"第 1 条，未修改业务代码）
        assertThat(count).isEqualTo(1);
        assertThat(captor.getValue().getQuantity()).isEqualTo(-5);
    }

    @Test
    @DisplayName("delete_不属于当前会员的id_不会删除")
    void delete_不属于当前会员的id_不会删除() {
        // 条件更新带上 member_id，DB 侧影响行数为 0
        when(cartItemMapper.updateByExampleSelective(any(OmsCartItem.class), any(OmsCartItemExample.class)))
                .thenReturn(0);

        int count = cartItemService.delete(1L, List.of(999L));

        ArgumentCaptor<OmsCartItemExample> exampleCaptor = ArgumentCaptor.forClass(OmsCartItemExample.class);
        verify(cartItemMapper).updateByExampleSelective(any(OmsCartItem.class), exampleCaptor.capture());

        assertThat(count).isZero();
        // 归属校验必须在 SQL 条件里，否则换个 id 就能删别人的购物车
        assertThat(conditions(exampleCaptor.getValue().getOredCriteria().get(0).getCriteria()))
                .anyMatch(c -> c.contains("member_id"))
                .anyMatch(c -> c.contains("id"));
    }

    @Test
    @DisplayName("clear_只清当前会员的行_且为逻辑删除")
    void clear_只清当前会员的行_且为逻辑删除() {
        when(cartItemMapper.updateByExampleSelective(any(OmsCartItem.class), any(OmsCartItemExample.class)))
                .thenReturn(3);

        int count = cartItemService.clear(1L);

        ArgumentCaptor<OmsCartItem> recordCaptor = ArgumentCaptor.forClass(OmsCartItem.class);
        ArgumentCaptor<OmsCartItemExample> exampleCaptor = ArgumentCaptor.forClass(OmsCartItemExample.class);
        verify(cartItemMapper).updateByExampleSelective(recordCaptor.capture(), exampleCaptor.capture());

        assertThat(count).isEqualTo(3);
        assertThat(recordCaptor.getValue().getDeleteStatus()).isEqualTo(1);
        assertThat(conditions(exampleCaptor.getValue().getOredCriteria().get(0).getCriteria()))
                .anyMatch(c -> c.contains("member_id"));
    }

    // ==================== 夹具 ====================

    private List<String> conditions(List<OmsCartItemExample.Criterion> criteria) {
        return criteria.stream().map(OmsCartItemExample.Criterion::getCondition).toList();
    }

    private UmsMember member() {
        UmsMember member = new UmsMember();
        member.setId(1L);
        member.setUsername("lijing");
        member.setNickname("李京");
        return member;
    }

    private PmsSkuStock sku(Long id, Long productId, String price) {
        PmsSkuStock sku = new PmsSkuStock();
        sku.setId(id);
        sku.setProductId(productId);
        sku.setPrice(new BigDecimal(price));
        sku.setSkuCode("SKU-" + id);
        sku.setStock(100);
        sku.setLockStock(0);
        return sku;
    }

    private PmsProduct product(Long id) {
        PmsProduct product = new PmsProduct();
        product.setId(id);
        product.setName("测试商品");
        product.setPic("http://img/1.jpg");
        product.setSubTitle("副标题");
        product.setProductSn("SN-100");
        product.setBrandName("测试品牌");
        product.setProductCategoryId(3L);
        return product;
    }
}
