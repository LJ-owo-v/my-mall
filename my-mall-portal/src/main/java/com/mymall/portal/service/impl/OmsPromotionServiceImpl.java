package com.mymall.portal.service.impl;

import com.mymall.portal.dao.PortalProductDao;
import com.mymall.portal.domain.CartPromotionItem;
import com.mymall.portal.domain.PromotionProduct;
import com.mymall.portal.model.OmsCartItem;
import com.mymall.portal.model.PmsProductFullReduction;
import com.mymall.portal.model.PmsProductLadder;
import com.mymall.portal.model.PmsSkuStock;
import com.mymall.portal.service.OmsPromotionService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OmsPromotionServiceImpl implements OmsPromotionService {

    @Autowired
    private PortalProductDao portalProductDao;
    /**
     * TODO 后面优化硬编码
     * 计算购物车商品的促销优惠价格
     *
     * 整体流程：
     * 1. 按 productId（SPU）对购物车商品分组，同一个商品的不同SKU合在一起算优惠
     * 2. 批量查询所有商品的促销信息（单品价/阶梯折扣/满减规则）
     * 3. 遍历每个商品，根据 promotionType 分别计算优惠：
     *    - promotionType=1：单品促销（直接降价，原价-促销价）
     *    - promotionType=3：阶梯打折（买够N件打X折，不满足则无优惠）
     *    - promotionType=4：满减（满X元减Y元，按价格比例分摊到每件）
     *    - 其他：无优惠
     *
     * @param cartItemList 购物车商品列表
     * @return 带优惠信息的购物车列表（比原购物车多了 reduceAmount、promotionMessage 等字段）
     */
    @Override
    public List<CartPromotionItem> calcCartPromotion(List<OmsCartItem> cartItemList) {
        // 1. 按 productId（SPU）分组
        Map<Long, List<OmsCartItem>> productCartMap = groupCartItemBySpu(cartItemList);

        // 2. 批量查询所有商品的促销信息
        List<PromotionProduct> promotionProductList = getPromotionProductList(cartItemList);

        // 3. 遍历每个商品，按促销类型分发到对应方法
        List<CartPromotionItem> result = new ArrayList<>();
        for (Map.Entry<Long, List<OmsCartItem>> entry : productCartMap.entrySet()) {
            Long productId = entry.getKey();
            List<OmsCartItem> itemList = entry.getValue();
            PromotionProduct promotionProduct = getPromotionProductById(productId, promotionProductList);

            // ★ 【#3 修复】促销有效期判断：过期的促销按"无优惠"处理。
            //   原实现只按 promotionType 分发，不判断时间 —— 实测商品 26（华为 P20）
            //   促销期 2023-01-10~2023-01-31 已过期 3 年多，下单仍给出 89 元优惠。
            //   语义：startTime/endTime 为 NULL 表示【不限制】，不计为过期。
            if (!isPromotionInEffect(promotionProduct)) {
                handleNoReduce(result, itemList, promotionProduct);   // 过期 → 无优惠（静默降级，不提示）
                continue;
            }

            switch (promotionProduct.getPromotionType()) {
                case 1:
                    calcSinglePromotion(result, itemList, promotionProduct);  // 单品促销
                    break;
                case 3:
                    calcLadderPromotion(result, itemList, promotionProduct);  // 阶梯打折
                    break;
                case 4:
                    calcFullReductionPromotion(result, itemList, promotionProduct, promotionProductList);  // 满减
                    break;
                default:
                    handleNoReduce(result, itemList, promotionProduct);      // 无优惠
                    break;
            }
        }
        return result;
    }

    /**
     * 判断促销是否在有效期内（修复 #3）
     *
     * 规则（NULL 表示不限制，不计为过期）：
     *   · promotionStartTime 不为空且【尚未开始】 → 无效
     *   · promotionEndTime   不为空且【已经结束】 → 无效
     *   · 其余情况                                  → 有效
     *
     * 注：本方法只做"是否可享优惠"的判断，不影响库存/积分等其它字段的计算
     *     （过期时走 handleNoReduce，仍会正确设置 realStock、integration、growth）。
     *
     * @param promotionProduct 促销商品信息（含促销起止时间）
     * @return true=促销有效；false=未开始或已过期
     */
    private boolean isPromotionInEffect(PromotionProduct promotionProduct) {
        Date now = new Date();

        Date startTime = promotionProduct.getPromotionStartTime();
        if (startTime != null && now.before(startTime)) {
            return false;   // 促销尚未开始
        }

        Date endTime = promotionProduct.getPromotionEndTime();
        if (endTime != null && now.after(endTime)) {
            return false;   // 促销已经结束
        }

        return true;
    }

    /**
     * 单品促销：直接降价，每件优惠 = 原价 - 促销价
     * 例如：原价6999，促销价6499，每件优惠500
     */
    private void calcSinglePromotion(List<CartPromotionItem> result,
                                     List<OmsCartItem> itemList,
                                     PromotionProduct promotionProduct) {
        // 遍历该商品的所有SKU（同商品不同规格）
        for (OmsCartItem item : itemList) {
            // 新建促销结果对象，把购物车信息拷贝过去
            CartPromotionItem cartPromotionItem = new CartPromotionItem();
            BeanUtils.copyProperties(item, cartPromotionItem);

            // 设置促销描述
            cartPromotionItem.setPromotionMessage("单品促销");

            // 找到对应SKU的价格信息（原价、促销价、库存）
            PmsSkuStock skuStock = getOriginalPrice(promotionProduct, item.getProductSkuId());
            BigDecimal originalPrice = skuStock.getPrice();

            // 设置原价（注意：单品促销时price用原价，优惠金额单独存）
            cartPromotionItem.setPrice(originalPrice);

            // 每件优惠金额 = 原价 - 促销价
            cartPromotionItem.setReduceAmount(originalPrice.subtract(skuStock.getPromotionPrice()));

            // 真实可售库存 = 总库存 - 锁定库存（别人下单未支付的）
            cartPromotionItem.setRealStock(skuStock.getStock() - skuStock.getLockStock());

            // 赠送积分和成长值
            cartPromotionItem.setIntegration(promotionProduct.getGiftPoint());
            cartPromotionItem.setGrowth(promotionProduct.getGiftGrowth());

            // 加入结果列表
            result.add(cartPromotionItem);
        }
    }


    /**
     * 阶梯打折：买够N件打X折，不满足则无优惠
     */
    private void calcLadderPromotion(List<CartPromotionItem> result,
                                     List<OmsCartItem> itemList,
                                     PromotionProduct promotionProduct) {
        int count = getCartItemCount(itemList);
        PmsProductLadder ladder = getProductLadder(count, promotionProduct.getProductLadderList());

        if (ladder == null) {
            handleNoReduce(result, itemList, promotionProduct);
            return;
        }

        for (OmsCartItem item : itemList) {
            CartPromotionItem cartPromotionItem = new CartPromotionItem();
            BeanUtils.copyProperties(item, cartPromotionItem);
            cartPromotionItem.setPromotionMessage(getLadderPromotionMessage(ladder));

            PmsSkuStock skuStock = getOriginalPrice(promotionProduct, item.getProductSkuId());
            BigDecimal originalPrice = skuStock.getPrice();
            // 每件优惠 = 原价 - 折扣 × 原价
            BigDecimal reduceAmount = originalPrice.subtract(ladder.getDiscount().multiply(originalPrice));

            cartPromotionItem.setReduceAmount(reduceAmount);
            cartPromotionItem.setRealStock(skuStock.getStock() - skuStock.getLockStock());
            cartPromotionItem.setIntegration(promotionProduct.getGiftPoint());
            cartPromotionItem.setGrowth(promotionProduct.getGiftGrowth());
            result.add(cartPromotionItem);
        }
    }

    /**
     * 满减：满X元减Y元，按价格比例分摊到每件商品
     */
    private void calcFullReductionPromotion(List<CartPromotionItem> result,
                                            List<OmsCartItem> itemList,
                                            PromotionProduct promotionProduct,
                                            List<PromotionProduct> promotionProductList) {
        BigDecimal totalAmount = getCartItemAmount(itemList, promotionProductList);
        PmsProductFullReduction fullReduction = getProductFullReduction(totalAmount, promotionProduct.getProductFullReductionList());

        if (fullReduction == null) {
            handleNoReduce(result, itemList, promotionProduct);
            return;
        }

        for (OmsCartItem item : itemList) {
            CartPromotionItem cartPromotionItem = new CartPromotionItem();
            BeanUtils.copyProperties(item, cartPromotionItem);
            cartPromotionItem.setPromotionMessage(getFullReductionPromotionMessage(fullReduction));

            PmsSkuStock skuStock = getOriginalPrice(promotionProduct, item.getProductSkuId());
            BigDecimal originalPrice = skuStock.getPrice();
            // 每件优惠 = (单件原价 / 总价) × 满减金额
            BigDecimal reduceAmount = originalPrice.divide(totalAmount, RoundingMode.HALF_EVEN)
                    .multiply(fullReduction.getReducePrice());

            cartPromotionItem.setReduceAmount(reduceAmount);
            cartPromotionItem.setRealStock(skuStock.getStock() - skuStock.getLockStock());
            cartPromotionItem.setIntegration(promotionProduct.getGiftPoint());
            cartPromotionItem.setGrowth(promotionProduct.getGiftGrowth());
            result.add(cartPromotionItem);
        }
    }


    /**
     * 获取满减促销消息
     */
    private String getFullReductionPromotionMessage(PmsProductFullReduction fullReduction) {
        StringBuilder sb = new StringBuilder();
        sb.append("满减优惠：");
        sb.append("满");
        sb.append(fullReduction.getFullPrice());
        sb.append("元，");
        sb.append("减");
        sb.append(fullReduction.getReducePrice());
        sb.append("元");
        return sb.toString();
    }
    /**
     * 根据购物车总价，找到满足条件的最高满减规则
     * 策略：按满减金额从高到低排序，找到第一个满足条件的（减得最多的）
     */
    private PmsProductFullReduction getProductFullReduction(BigDecimal totalAmount,
                                                            List<PmsProductFullReduction> fullReductionList) {
        return fullReductionList.stream()
                .filter(fr -> totalAmount.compareTo(fr.getFullPrice()) >= 0)  // 过滤满足金额条件的
                .max(Comparator.comparing(PmsProductFullReduction::getFullPrice))  // 取满减金额最高的
                .orElse(null);
    }


    /**
     * 获取购物车中指定商品的总价（按原价计算）
     * 同一个商品的所有SKU：原价 × 数量，全部加起来
     * 用于判断是否满足满减条件
     */
    private BigDecimal getCartItemAmount(List<OmsCartItem> itemList,
                                         List<PromotionProduct> promotionProductList) {
        BigDecimal amount = new BigDecimal(0);

        for (OmsCartItem item : itemList) {
            // 找到该商品的促销信息（包含SKU价格）
            PromotionProduct promotionProduct = getPromotionProductById(item.getProductId(), promotionProductList);
            // 找到对应SKU的原价
            PmsSkuStock skuStock = getOriginalPrice(promotionProduct, item.getProductSkuId());
            // 累加：原价 × 数量
            amount = amount.add(skuStock.getPrice().multiply(new BigDecimal(item.getQuantity())));
        }
        return amount;
    }


    /**
     * 对没满足优惠条件的商品进行处理
     * 和单品促销逻辑一样，只是优惠金额=0，提示"无优惠"
     * 适用场景：
     * 1. promotionType=0（无促销）
     * 2. 阶梯打折但购买数量不够
     * 3. 满减但总价不够
     */
    private void handleNoReduce(List<CartPromotionItem> cartPromotionItemList,
                                List<OmsCartItem> itemList,
                                PromotionProduct promotionProduct) {
        for (OmsCartItem item : itemList) {
            // 拷贝购物车信息
            CartPromotionItem cartPromotionItem = new CartPromotionItem();
            BeanUtils.copyProperties(item, cartPromotionItem);

            // 无优惠
            cartPromotionItem.setPromotionMessage("无优惠");
            cartPromotionItem.setReduceAmount(new BigDecimal(0));  // 优惠金额=0

            // 查SKU库存（加了判空，防止sku不存在时报空指针）
            PmsSkuStock skuStock = getOriginalPrice(promotionProduct, item.getProductSkuId());
            if (skuStock != null) {
                cartPromotionItem.setRealStock(skuStock.getStock() - skuStock.getLockStock());
            }

            // 赠送积分和成长值（即使无优惠也可能送积分）
            cartPromotionItem.setIntegration(promotionProduct.getGiftPoint());
            cartPromotionItem.setGrowth(promotionProduct.getGiftGrowth());

            cartPromotionItemList.add(cartPromotionItem);
        }
    }


    /**
     * 获取打折优惠的促销信息
     */
    private String getLadderPromotionMessage(PmsProductLadder ladder) {
        StringBuilder sb = new StringBuilder();
        sb.append("打折优惠：");
        sb.append("满");
        sb.append(ladder.getCount());
        sb.append("件，");
        sb.append("打");
        sb.append(ladder.getDiscount().multiply(new BigDecimal(10)));
        sb.append("折");
        return sb.toString();
    }

    /**
     * 根据购买商品数量，找到满足条件的最高阶梯折扣
     * 策略：按数量从大到小排序，找到第一个满足条件的（折扣最大的）
     */
    private PmsProductLadder getProductLadder(int count, List<PmsProductLadder> productLadderList) {
        return productLadderList.stream()
                .filter(ladder -> count >= ladder.getCount())   // 过滤满足数量条件的
                .max(Comparator.comparingInt(PmsProductLadder::getCount))  // 取数量要求最高的（折扣最大）
                .orElse(null);
    }


    /**
     * 获取购物车中指定商品的数量
     */
    private int getCartItemCount(List<OmsCartItem> itemList) {
        return itemList.stream()
                .mapToInt(OmsCartItem::getQuantity)
                .sum();
    }
    /**
     * 根据skuId，从商品的SKU列表中找到对应的SKU信息
     * 包含原价、促销价、库存等
     */
    private PmsSkuStock getOriginalPrice(PromotionProduct promotionProduct, Long productSkuId) {

        return promotionProduct.getSkuStockList().stream()
                .filter(sku -> productSkuId.equals(sku.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据商品ID，从促销信息列表中找到对应的促销配置
     * 相当于在内存里做一次 WHERE id = ?
     */
    private PromotionProduct getPromotionProductById(Long productId, List<PromotionProduct> promotionProductList) {
        return promotionProductList.stream()                              // ① 把列表转成流
                .filter(p -> productId.equals(p.getId())) // ② 过滤：只保留id匹配的商品
                .findFirst()                                              // ③ 取第一个匹配的元素（可能没有）
                .orElse(null);                                      // ④ 没找到就返回null
    }


    /**
     * 根据购物车商品ID列表，批量查询商品的促销信息
     * 包括：单品促销价、阶梯折扣、满减规则、SKU库存等
     */
    private List<PromotionProduct> getPromotionProductList(List<OmsCartItem> cartItemList) {
        List<Long> productIdList = cartItemList.stream()   // ① 转成流
                .map(OmsCartItem::getProductId)            // ② 映射：购物车项 → 商品ID
                .collect(Collectors.toList());             // ③ 收集成List
        return portalProductDao.getPromotionProductList(productIdList);
    }



    /**
     * 以spu为单位对购物车中商品进行分组
     */
    private Map<Long, List<OmsCartItem>> groupCartItemBySpu(List<OmsCartItem> cartItemList) {
        return cartItemList.stream()                          // ① 把列表转成流   [购物车1, 购物车2, 购物车3, 购物车4] → 流
                .collect(Collectors.groupingBy(              // ② 分组收集
                        OmsCartItem::getProductId,           // ③ 按 productId 分组（key）
                        TreeMap::new,                        // ④ 用 TreeMap 装结果（有序）
                        Collectors.toList()                  // ⑤ 每组的值用 List 收集
                ));
    }


}
