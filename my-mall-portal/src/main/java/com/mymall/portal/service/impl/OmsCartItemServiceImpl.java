package com.mymall.portal.service.impl;

import com.mymall.portal.domain.CartProduct;

import cn.hutool.core.collection.CollUtil;
import com.mymall.common.exception.Asserts;
import com.mymall.portal.dao.OmsCartItemMapper;
import com.mymall.portal.dao.PmsProductMapper;
import com.mymall.portal.dao.PmsSkuStockMapper;
import com.mymall.portal.dao.PortalProductDao;
import com.mymall.portal.domain.CartPromotionItem;
import com.mymall.portal.model.OmsCartItem;
import com.mymall.portal.model.OmsCartItemExample;
import com.mymall.portal.model.PmsProduct;
import com.mymall.portal.model.PmsSkuStock;
import com.mymall.portal.model.UmsMember;
import com.mymall.portal.service.OmsCartItemService;
import com.mymall.portal.service.OmsPromotionService;
import com.mymall.portal.service.UmsMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OmsCartItemServiceImpl implements OmsCartItemService {
    @Autowired
    private OmsCartItemMapper cartItemMapper;
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private OmsPromotionService promotionService;
    @Autowired
    private PortalProductDao productDao;
    // ★ 【#20】加购时按 skuId 回查，服务端定价 + 补全商品信息
    @Autowired
    private PmsSkuStockMapper skuStockMapper;
    @Autowired
    private PmsProductMapper productMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int add(OmsCartItem cartItem) {
        int count;

        // ① 获取当前登录会员
        UmsMember currentMember = memberService.getCurrentMember();

        // ★ 【#20 修复】按 productSkuId 回查 SKU 与商品，服务端补全/覆盖关键字段。
        //
        // 修复前的缺陷（实测确认，前面所有优化都拦不住）：
        //   ① 漏传 productId → getCartItem 用 product_id = null 查询 → 加购直接 HTTP 500；
        //   ② 漏传 price    → oms_cart_item.price = NULL → 下单 calcCartAmount 里
        //                     item.getPrice().multiply(...) → NPE → HTTP 500；
        //   ③ 传错价（如 0.01）→ 后端原样落库 → 用户可按错误价格下单（资损风险）；
        //   ④ 不传商品信息  → product_name/product_pic 为 NULL → 订单明细空白。
        //
        // 修复思路（对齐主流做法"风格 A"）：价格是服务端资产，客户端只能"看"不能"说"。
        //   加购只应传 productSkuId + quantity，其余字段一律由服务端按 skuId 回查填充，
        //   前端传来的 price/productName 等【一律忽略并覆盖】。
        //
        // 注：老项目 mall-swarm 的 add() 同样不查 SKU（信任前端传价），属继承的既有缺陷；
        //     此处属【有意的行为变更】（安全加固），需在台账登记。
        if (cartItem.getProductSkuId() == null) {
            Asserts.fail("请选择商品规格");
        }
        PmsSkuStock skuStock = skuStockMapper.selectByPrimaryKey(cartItem.getProductSkuId());
        if (skuStock == null) {
            Asserts.fail("商品规格不存在，请重新选择");
        }
        // 服务端定价：覆盖前端传入的任何价格
        cartItem.setProductId(skuStock.getProductId());
        cartItem.setPrice(skuStock.getPrice());
        cartItem.setProductSkuCode(skuStock.getSkuCode());

        // 补全商品信息（名称/图片/分类/品牌/货号），避免订单明细出现空白字段
        PmsProduct product = productMapper.selectByPrimaryKey(skuStock.getProductId());
        if (product != null) {
            cartItem.setProductName(product.getName());
            // SKU 有独立图片时优先用 SKU 的，否则回退到商品主图
            cartItem.setProductPic(skuStock.getPic() != null ? skuStock.getPic() : product.getPic());
            cartItem.setProductSubTitle(product.getSubTitle());
            cartItem.setProductSn(product.getProductSn());
            cartItem.setProductBrand(product.getBrandName());
            cartItem.setProductCategoryId(product.getProductCategoryId());
        }

        // ② 填充会员信息
        cartItem.setMemberId(currentMember.getId());
        cartItem.setMemberNickname(currentMember.getNickname());
        cartItem.setDeleteStatus(0);  // 0=未删除

        // ③ 查询购物车中是否已有该商品（按 memberId + productId + skuId 判断）
        OmsCartItem existCartItem = getCartItem(cartItem);

        if (existCartItem == null) {
            // ④ 购物车没有该商品，新增一条
            cartItem.setCreateDate(new Date());
            count = cartItemMapper.insert(cartItem);
        } else {
            // ⑤ 购物车已有该商品，数量累加
            cartItem.setModifyDate(new Date());
            existCartItem.setQuantity(existCartItem.getQuantity() + cartItem.getQuantity());
            count = cartItemMapper.updateByPrimaryKey(existCartItem);
        }

        return count;
    }

    @Override
    public List<OmsCartItem> list(Long memberId) {
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andDeleteStatusEqualTo(0).andMemberIdEqualTo(memberId);
        return cartItemMapper.selectByExample(example);
    }

    @Override
    public List<CartPromotionItem> listPromotion(Long memberId, List<Long> cartIds) {
        // ① 查出该用户所有购物车商品
        List<OmsCartItem> cartItemList = list(memberId);

        // ② 如果传了选中的购物车id，过滤出选中的商品（没传就用全部）
        if (CollUtil.isNotEmpty(cartIds)) {
            cartItemList = cartItemList.stream()
                    .filter(item -> cartIds.contains(item.getId()))
                    .collect(Collectors.toList());
        }

        List<CartPromotionItem> cartPromotionItemList = new ArrayList<>();

        // ③ 购物车不为空，调用促销服务计算优惠（满减、折扣、优惠券等）
        if (!CollectionUtils.isEmpty(cartItemList)) {
            cartPromotionItemList = promotionService.calcCartPromotion(cartItemList);
        }

        // ④ 返回带促销信息的购物车列表
        return cartPromotionItemList;
    }

    @Override
    public int updateQuantity(Long id, Long memberId, Integer quantity) {
        OmsCartItem cartItem = new OmsCartItem();
        cartItem.setQuantity(quantity);
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andDeleteStatusEqualTo(0)
                .andIdEqualTo(id).andMemberIdEqualTo(memberId);
        return cartItemMapper.updateByExampleSelective(cartItem, example);
    }

    @Override
    public CartProduct getCartProduct(Long productId) {
        return productDao.getCartProduct(productId);
    }

    @Override
    public int updateAttr(OmsCartItem cartItem) {
        //删除原购物车信息
        OmsCartItem updateCart = new OmsCartItem();
        updateCart.setId(cartItem.getId());
        updateCart.setModifyDate(new Date());
        updateCart.setDeleteStatus(1);
        cartItemMapper.updateByPrimaryKeySelective(updateCart);
        cartItem.setId(null);
        add(cartItem);
        return 1;
    }

    @Override
    public int delete(Long memberId, List<Long> ids) {
        // ① 构建更新对象：只更新 deleteStatus 字段
        OmsCartItem record = new OmsCartItem();
        record.setDeleteStatus(1);  // 1=已删除

        // ② 构建更新条件：id在列表中 + 必须是当前用户的
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria()
                .andIdIn(ids)
                .andMemberIdEqualTo(memberId);

        // ③ 逻辑删除（不是物理删除，只是标记为已删除）
        return cartItemMapper.updateByExampleSelective(record, example);
    }

    @Override
    public int clear(Long memberId) {
        OmsCartItem record = new OmsCartItem();
        record.setDeleteStatus(1);
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andMemberIdEqualTo(memberId);
        return cartItemMapper.updateByExampleSelective(record,example);
    }


    /**
     * 根据会员id,商品id和规格获取购物车中商品
     */
    private OmsCartItem getCartItem(OmsCartItem cartItem) {
        OmsCartItemExample example = new OmsCartItemExample();
        OmsCartItemExample.Criteria criteria = example.createCriteria().andMemberIdEqualTo(cartItem.getMemberId())
                .andProductIdEqualTo(cartItem.getProductId()).andDeleteStatusEqualTo(0);
        if (cartItem.getProductSkuId()!=null) {
            criteria.andProductSkuIdEqualTo(cartItem.getProductSkuId());
        }
        List<OmsCartItem> cartItemList = cartItemMapper.selectByExample(example);
        if (!CollectionUtils.isEmpty(cartItemList)) {
            return cartItemList.get(0);
        }
        return null;
    }

}
