package com.mymall.portal.service.impl;

import com.mymall.portal.dao.PortalProductDao;
import com.mymall.portal.domain.CartPromotionItem;
import com.mymall.portal.domain.PromotionProduct;
import com.mymall.portal.model.OmsCartItem;
import com.mymall.portal.model.PmsProductFullReduction;
import com.mymall.portal.model.PmsProductLadder;
import com.mymall.portal.model.PmsSkuStock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * OmsPromotionServiceImpl 纯单元测试。
 *
 * <p>重点在分支：促销类型（单品/阶梯/满减/无）、促销时间窗、以及满减分摊的精度。
 * 满减分摊的断言口径是"各行优惠之和 == 满减额"，而不是逐行比对小数 —— 后者换个舍入方式就红。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OmsPromotionServiceImpl")
class OmsPromotionServiceImplTest {

    @Mock private PortalProductDao portalProductDao;
    @InjectMocks private OmsPromotionServiceImpl promotionService;

    @Test
    @DisplayName("calcCartPromotion_空购物车_返回空列表")
    void calcCartPromotion_空购物车_返回空列表() {
        when(portalProductDao.getPromotionProductList(anyList())).thenReturn(new ArrayList<>());

        List<CartPromotionItem> result = promotionService.calcCartPromotion(new ArrayList<>());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("calcCartPromotion_无促销商品_优惠为0且保留加购价格")
    void calcCartPromotion_无促销商品_优惠为0且保留加购价格() {
        OmsCartItem cartItem = cartItem(1L, 100L, 9001L, 2);
        PromotionProduct product = product(100L, 0);
        product.setSkuStockList(List.of(sku(9001L, "100", "100", 50, 5)));
        when(portalProductDao.getPromotionProductList(anyList())).thenReturn(List.of(product));

        List<CartPromotionItem> result = promotionService.calcCartPromotion(List.of(cartItem));

        assertThat(result).hasSize(1);
        CartPromotionItem item = result.get(0);
        assertThat(item.getReduceAmount()).isEqualByComparingTo("0");
        assertThat(item.getPromotionMessage()).isEqualTo("无优惠");
        // 注意：无促销分支不读 SKU 的 price，price 沿用购物车行上的值
        // （该值在 #20 之后由服务端按 skuId 回填，客户端无法伪造）
        assertThat(item.getPrice()).isEqualByComparingTo(cartItem.getPrice());
        // 真实可售 = stock - lockStock，下单 hasStock 判断直接吃这个值
        assertThat(item.getRealStock()).isEqualTo(45);
    }

    @Test
    @DisplayName("calcCartPromotion_阶梯价命中中间档_取对应折扣")
    void calcCartPromotion_阶梯价命中中间档_取对应折扣() {
        OmsCartItem cartItem = cartItem(1L, 100L, 9001L, 3);
        PromotionProduct product = product(100L, 3);
        product.setSkuStockList(List.of(sku(9001L, "100", "100", 50, 0)));
        product.setProductLadderList(List.of(
                ladder(2, "0.9"),   // 满 2 件 9 折
                ladder(5, "0.8"))); // 满 5 件 8 折
        when(portalProductDao.getPromotionProductList(anyList())).thenReturn(List.of(product));

        List<CartPromotionItem> result = promotionService.calcCartPromotion(List.of(cartItem));

        // 买 3 件够 2 件档、不够 5 件档 → 走 9 折，每件优惠 100 - 90 = 10
        assertThat(result.get(0).getReduceAmount()).isEqualByComparingTo("10");
        assertThat(result.get(0).getPromotionMessage()).contains("满2件");
    }

    @Test
    @DisplayName("calcCartPromotion_数量正好等于阶梯阈值_取高档折扣")
    void calcCartPromotion_数量正好等于阶梯阈值_取高档折扣() {
        OmsCartItem cartItem = cartItem(1L, 100L, 9001L, 5);
        PromotionProduct product = product(100L, 3);
        product.setSkuStockList(List.of(sku(9001L, "100", "100", 50, 0)));
        product.setProductLadderList(List.of(
                ladder(2, "0.9"),
                ladder(5, "0.8")));
        when(portalProductDao.getPromotionProductList(anyList())).thenReturn(List.of(product));

        List<CartPromotionItem> result = promotionService.calcCartPromotion(List.of(cartItem));

        // 边界必须取"高档"：count >= ladder.count 是包含等于的，不能退化成 9 折
        assertThat(result.get(0).getReduceAmount()).isEqualByComparingTo("20");
        assertThat(result.get(0).getPromotionMessage()).contains("满5件");
    }

    @Test
    @DisplayName("calcCartPromotion_阶梯数量不达标_降级为无优惠")
    void calcCartPromotion_阶梯数量不达标_降级为无优惠() {
        OmsCartItem cartItem = cartItem(1L, 100L, 9001L, 1);
        PromotionProduct product = product(100L, 3);
        product.setSkuStockList(List.of(sku(9001L, "100", "100", 50, 0)));
        product.setProductLadderList(List.of(ladder(3, "0.8")));
        when(portalProductDao.getPromotionProductList(anyList())).thenReturn(List.of(product));

        List<CartPromotionItem> result = promotionService.calcCartPromotion(List.of(cartItem));

        assertThat(result.get(0).getReduceAmount()).isEqualByComparingTo("0");
        assertThat(result.get(0).getPromotionMessage()).isEqualTo("无优惠");
    }

    @Test
    @DisplayName("calcCartPromotion_满减命中_各行优惠之和等于满减额")
    void calcCartPromotion_满减命中_各行优惠之和等于满减额() {
        // 缺陷 #4 的原始复现数据：649 + 699 + 649 = 1997，满 1000 减 120
        List<OmsCartItem> cart = List.of(
                cartItem(1L, 100L, 9001L, 1),
                cartItem(2L, 100L, 9002L, 1),
                cartItem(3L, 100L, 9003L, 1));
        PromotionProduct product = product(100L, 4);
        product.setSkuStockList(List.of(
                sku(9001L, "649", "649", 50, 0),
                sku(9002L, "699", "699", 50, 0),
                sku(9003L, "649", "649", 50, 0)));
        product.setProductFullReductionList(List.of(fullReduction("1000", "120")));
        when(portalProductDao.getPromotionProductList(anyList())).thenReturn(List.of(product));

        List<CartPromotionItem> result = promotionService.calcCartPromotion(cart);

        // 核心断言：分摊之和必须精确等于承诺的 120，不能因为逐行舍入掉成 118.8
        assertThat(sumReduce(result)).isEqualByComparingTo("120");
        assertThat(result).allSatisfy(item ->
                assertThat(item.getPromotionMessage()).contains("满1000"));
    }

    @Test
    @DisplayName("calcCartPromotion_满减不可整除_最后一行补齐差额")
    void calcCartPromotion_满减不可整除_最后一行补齐差额() {
        // 三行各 100，减 100 → 每行 33.3333…，靠最后一行吸收误差
        List<OmsCartItem> cart = List.of(
                cartItem(1L, 100L, 9001L, 1),
                cartItem(2L, 100L, 9002L, 1),
                cartItem(3L, 100L, 9003L, 1));
        PromotionProduct product = product(100L, 4);
        product.setSkuStockList(List.of(
                sku(9001L, "100", "100", 50, 0),
                sku(9002L, "100", "100", 50, 0),
                sku(9003L, "100", "100", 50, 0)));
        product.setProductFullReductionList(List.of(fullReduction("100", "100")));
        when(portalProductDao.getPromotionProductList(anyList())).thenReturn(List.of(product));

        List<CartPromotionItem> result = promotionService.calcCartPromotion(cart);

        assertThat(sumReduce(result)).isEqualByComparingTo("100");
        // 前两行均分，最后一行是"减法补齐"，不会比前面少
        assertThat(result.get(2).getReduceAmount())
                .isGreaterThanOrEqualTo(result.get(0).getReduceAmount());
    }

    @Test
    @DisplayName("calcCartPromotion_促销已过期_不计优惠")
    void calcCartPromotion_促销已过期_不计优惠() {
        OmsCartItem cartItem = cartItem(1L, 100L, 9001L, 1);
        PromotionProduct product = product(100L, 1);
        product.setSkuStockList(List.of(sku(9001L, "100", "80", 50, 0)));
        // 相对时间：昨天结束 —— 不写死日期，避免测试随时间失效
        product.setPromotionEndTime(new Date(System.currentTimeMillis() - 24 * 3600 * 1000L));
        when(portalProductDao.getPromotionProductList(anyList())).thenReturn(List.of(product));

        List<CartPromotionItem> result = promotionService.calcCartPromotion(List.of(cartItem));

        // 缺陷 #3：过期促销仍给优惠 → 这里必须降级为无优惠
        assertThat(result.get(0).getReduceAmount()).isEqualByComparingTo("0");
        assertThat(result.get(0).getPromotionMessage()).isEqualTo("无优惠");
    }

    @Test
    @DisplayName("calcCartPromotion_促销未开始_不计优惠")
    void calcCartPromotion_促销未开始_不计优惠() {
        OmsCartItem cartItem = cartItem(1L, 100L, 9001L, 1);
        PromotionProduct product = product(100L, 1);
        product.setSkuStockList(List.of(sku(9001L, "100", "80", 50, 0)));
        product.setPromotionStartTime(new Date(System.currentTimeMillis() + 24 * 3600 * 1000L));
        when(portalProductDao.getPromotionProductList(anyList())).thenReturn(List.of(product));

        List<CartPromotionItem> result = promotionService.calcCartPromotion(List.of(cartItem));

        assertThat(result.get(0).getReduceAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("calcCartPromotion_单品促销_优惠等于原价减促销价")
    void calcCartPromotion_单品促销_优惠等于原价减促销价() {
        OmsCartItem cartItem = cartItem(1L, 100L, 9001L, 2);
        PromotionProduct product = product(100L, 1);
        product.setSkuStockList(List.of(sku(9001L, "6999", "6499", 20, 2)));
        when(portalProductDao.getPromotionProductList(anyList())).thenReturn(List.of(product));

        List<CartPromotionItem> result = promotionService.calcCartPromotion(List.of(cartItem));

        assertThat(result.get(0).getPrice()).isEqualByComparingTo("6999");
        assertThat(result.get(0).getReduceAmount()).isEqualByComparingTo("500");
        assertThat(result.get(0).getRealStock()).isEqualTo(18);
    }

    @Test
    @DisplayName("calcCartPromotion_促销信息缺失_当前实现抛NPE")
    void calcCartPromotion_促销信息缺失_当前实现抛NPE() {
        OmsCartItem cartItem = cartItem(1L, 100L, 9001L, 1);
        // DAO 没有返回该商品的促销信息（商品被删/促销表缺行）
        when(portalProductDao.getPromotionProductList(anyList())).thenReturn(new ArrayList<>());

        // 如实记录当前行为：promotionProduct 为 null 时 isPromotionInEffect 直接解引用 → NPE，
        // 整个购物车列表接口会 500（详见回报"疑似缺陷"第 5 条，未修改业务代码）
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> promotionService.calcCartPromotion(List.of(cartItem)))
                .isInstanceOf(NullPointerException.class);
    }

    // ==================== 夹具 ====================

    private BigDecimal sumReduce(List<CartPromotionItem> items) {
        return items.stream()
                .map(i -> i.getReduceAmount().multiply(new BigDecimal(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private OmsCartItem cartItem(Long cartId, Long productId, Long skuId, int quantity) {
        OmsCartItem item = new OmsCartItem();
        item.setId(cartId);
        item.setProductId(productId);
        item.setProductSkuId(skuId);
        item.setQuantity(quantity);
        item.setPrice(new BigDecimal("100"));   // 加购时由服务端按 skuId 写入
        item.setProductName("测试商品");
        return item;
    }

    private PromotionProduct product(Long id, int promotionType) {
        PromotionProduct product = new PromotionProduct();
        product.setId(id);
        product.setName("测试商品");
        product.setPromotionType(promotionType);
        product.setGiftPoint(10);
        product.setGiftGrowth(10);
        return product;
    }

    private PmsSkuStock sku(Long id, String price, String promotionPrice, int stock, int lockStock) {
        PmsSkuStock sku = new PmsSkuStock();
        sku.setId(id);
        sku.setPrice(new BigDecimal(price));
        sku.setPromotionPrice(new BigDecimal(promotionPrice));
        sku.setStock(stock);
        sku.setLockStock(lockStock);
        return sku;
    }

    private PmsProductLadder ladder(int count, String discount) {
        PmsProductLadder ladder = new PmsProductLadder();
        ladder.setCount(count);
        ladder.setDiscount(new BigDecimal(discount));
        return ladder;
    }

    private PmsProductFullReduction fullReduction(String fullPrice, String reducePrice) {
        PmsProductFullReduction reduction = new PmsProductFullReduction();
        reduction.setFullPrice(new BigDecimal(fullPrice));
        reduction.setReducePrice(new BigDecimal(reducePrice));
        return reduction;
    }
}
