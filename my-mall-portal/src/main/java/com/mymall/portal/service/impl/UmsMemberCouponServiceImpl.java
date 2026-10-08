package com.mymall.portal.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.mymall.common.exception.Asserts;
import com.mymall.portal.dao.*;
import com.mymall.portal.domain.CartPromotionItem;
import com.mymall.portal.domain.SmsCouponHistoryDetail;
import com.mymall.portal.model.*;
import com.mymall.portal.service.UmsMemberCouponService;
import com.mymall.portal.service.UmsMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class UmsMemberCouponServiceImpl implements UmsMemberCouponService {
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private SmsCouponMapper couponMapper;
    @Autowired
    private SmsCouponHistoryMapper couponHistoryMapper;
    @Autowired
    private SmsCouponHistoryDao couponHistoryDao;
    @Autowired
    private SmsCouponProductRelationMapper couponProductRelationMapper;
    @Autowired
    private SmsCouponProductCategoryRelationMapper couponProductCategoryRelationMapper;
    @Autowired
    private PmsProductMapper productMapper;
    @Override
    @Transactional
    public void add(Long couponId) {
        // ① 获取当前登录会员
        UmsMember currentMember = memberService.getCurrentMember();

        // ② 查优惠券信息
        SmsCoupon coupon = couponMapper.selectByPrimaryKey(couponId);
        if (coupon == null) {
            Asserts.fail("优惠券不存在");
        }

        // ③ 判断库存
        if (coupon.getCount() <= 0) {
            Asserts.fail("优惠券已经领完了");
        }

        // ④ 判断领取时间
        Date now = new Date();
        if (now.before(coupon.getEnableTime())) {
            Asserts.fail("优惠券还没到领取时间");
        }

        // ⑤ 判断用户是否已领过（超过每人限领数量）
        SmsCouponHistoryExample couponHistoryExample = new SmsCouponHistoryExample();
        couponHistoryExample.createCriteria()
                .andCouponIdEqualTo(couponId)
                .andMemberIdEqualTo(currentMember.getId());
        long count = couponHistoryMapper.countByExample(couponHistoryExample);
        if (count >= coupon.getPerLimit()) {
            Asserts.fail("您已经领取过该优惠券");
        }

        // ⑥ 生成领取记录
        SmsCouponHistory couponHistory = new SmsCouponHistory();
        couponHistory.setCouponId(couponId);
        couponHistory.setCouponCode(generateCouponCode(currentMember.getId()));  // 生成唯一券码
        couponHistory.setCreateTime(now);
        couponHistory.setMemberId(currentMember.getId());
        couponHistory.setMemberNickname(currentMember.getNickname());
        couponHistory.setGetType(1);      // 1=主动领取
        couponHistory.setUseStatus(0);    // 0=未使用
        couponHistoryMapper.insert(couponHistory);

        // ⑦ 更新优惠券表：库存-1，领取数+1
        // TODO 后期优化：高并发下可能超发，改用乐观锁
        //  UPDATE sms_coupon SET count = count - 1, receive_count = receive_count + 1
        //  WHERE id = #{id} AND count > 0
        //  返回影响行数=0说明没抢到，回滚事务并提示"手慢了，优惠券已被抢完"
        coupon.setCount(coupon.getCount() - 1);
        coupon.setReceiveCount(coupon.getReceiveCount() == null ? 1 : coupon.getReceiveCount() + 1);
        couponMapper.updateByPrimaryKey(coupon);
    }

    @Override
    public List<SmsCouponHistory> listHistory(Integer useStatus) {
        UmsMember currentMember = memberService.getCurrentMember();
        SmsCouponHistoryExample couponHistoryExample=new SmsCouponHistoryExample();
        SmsCouponHistoryExample.Criteria criteria = couponHistoryExample.createCriteria();
        criteria.andMemberIdEqualTo(currentMember.getId());
        if(useStatus!=null){
            criteria.andUseStatusEqualTo(useStatus);
        }
        return couponHistoryMapper.selectByExample(couponHistoryExample);
    }

    /**
     * 查询购物车可用/不可用优惠券
     * @param cartItemList 购物车商品列表
     * @param type 1=返回可用优惠券，其他=返回不可用优惠券
     */
    @Override
    public List<SmsCouponHistoryDetail> listCart(List<CartPromotionItem> cartItemList, Integer type) {
        UmsMember currentMember = memberService.getCurrentMember();
        Date now = new Date();

        // ① 查出该用户所有优惠券（含优惠券详情、关联的分类/商品）
        List<SmsCouponHistoryDetail> allList = couponHistoryDao.getDetailList(currentMember.getId(), null);

        // ② 遍历每张优惠券，判断是否可用
        List<SmsCouponHistoryDetail> enableList = new ArrayList<>();   // 可用
        List<SmsCouponHistoryDetail> disableList = new ArrayList<>();  // 不可用

        for (SmsCouponHistoryDetail couponHistoryDetail : allList) {
            Integer useType = couponHistoryDetail.getCoupon().getUseType();  // 0全场/1指定分类/2指定商品
            BigDecimal minPoint = couponHistoryDetail.getCoupon().getMinPoint();  // 满减门槛
            Date endTime = couponHistoryDetail.getCoupon().getEndTime();          // 过期时间

            if (useType.equals(0)) {
                // 0->全场通用：算购物车总价是否满足满减门槛
                BigDecimal totalAmount = calcTotalAmount(cartItemList);
                if (now.before(endTime) && totalAmount.subtract(minPoint).intValue() >= 0) {
                    enableList.add(couponHistoryDetail);
                } else {
                    disableList.add(couponHistoryDetail);
                }
            } else if (useType.equals(1)) {
                // 1->指定分类：算指定分类商品的总价是否满足门槛
                List<Long> productCategoryIds = new ArrayList<>();
                for (SmsCouponProductCategoryRelation relation : couponHistoryDetail.getCategoryRelationList()) {
                    productCategoryIds.add(relation.getProductCategoryId());
                }
                BigDecimal totalAmount = calcTotalAmountByproductCategoryId(cartItemList, productCategoryIds);
                // 需要同时满足：未过期 + 有指定分类商品 + 满足满减门槛
                if (now.before(endTime) && totalAmount.intValue() > 0 && totalAmount.subtract(minPoint).intValue() >= 0) {
                    enableList.add(couponHistoryDetail);
                } else {
                    disableList.add(couponHistoryDetail);
                }
            } else if (useType.equals(2)) {
                // 2->指定商品：算指定商品的总价是否满足门槛
                List<Long> productIds = new ArrayList<>();
                for (SmsCouponProductRelation relation : couponHistoryDetail.getProductRelationList()) {
                    productIds.add(relation.getProductId());
                }
                BigDecimal totalAmount = calcTotalAmountByProductId(cartItemList, productIds);
                if (now.before(endTime) && totalAmount.intValue() > 0 && totalAmount.subtract(minPoint).intValue() >= 0) {
                    enableList.add(couponHistoryDetail);
                } else {
                    disableList.add(couponHistoryDetail);
                }
            }
        }

        // ③ type=1返回可用，否则返回不可用
        if (type.equals(1)) {
            return enableList;
        } else {
            return disableList;
        }
    }

    /**
     * 根据商品ID查询该商品可用的优惠券
     * 包括：全场通用券 + 指定该商品的券 + 指定该商品分类的券
     */
    @Override
    public List<SmsCoupon> listByProduct(Long productId) {
        List<Long> allCouponIds = new ArrayList<>();

        // ① 查指定商品关联的优惠券（useType=2，指定商品）
        SmsCouponProductRelationExample cprExample = new SmsCouponProductRelationExample();
        cprExample.createCriteria().andProductIdEqualTo(productId);
        List<SmsCouponProductRelation> cprList = couponProductRelationMapper.selectByExample(cprExample);
        if (CollUtil.isNotEmpty(cprList)) {
            List<Long> couponIds = cprList.stream()
                    .map(SmsCouponProductRelation::getCouponId)
                    .collect(Collectors.toList());
            allCouponIds.addAll(couponIds);
        }

        // ② 查商品所属分类关联的优惠券（useType=1，指定分类）
        PmsProduct product = productMapper.selectByPrimaryKey(productId);
        SmsCouponProductCategoryRelationExample cpcrExample = new SmsCouponProductCategoryRelationExample();
        cpcrExample.createCriteria().andProductCategoryIdEqualTo(product.getProductCategoryId());
        List<SmsCouponProductCategoryRelation> cpcrList = couponProductCategoryRelationMapper.selectByExample(cpcrExample);
        if (CollUtil.isNotEmpty(cpcrList)) {
            List<Long> couponIds = cpcrList.stream()
                    .map(SmsCouponProductCategoryRelation::getCouponId)
                    .collect(Collectors.toList());
            allCouponIds.addAll(couponIds);
        }

        // ③ 没有关联的优惠券，直接返回空
        if (CollUtil.isEmpty(allCouponIds)) {
            return new ArrayList<>();
        }

        // ④ 查询可用优惠券：
        //    条件1：全场通用券（useType=0），在有效期内
        //    条件2：指定商品/分类券（useType!=0），id在关联列表中，在有效期内
        SmsCouponExample couponExample = new SmsCouponExample();
        couponExample.createCriteria()
                .andEndTimeGreaterThan(new Date())        // 未过期
                .andStartTimeLessThan(new Date())         // 已生效
                .andUseTypeEqualTo(0);                    // 全场通用
        couponExample.or(couponExample.createCriteria()
                .andEndTimeGreaterThan(new Date())
                .andStartTimeLessThan(new Date())
                .andUseTypeNotEqualTo(0)                // 指定商品/分类
                .andIdIn(allCouponIds));                // 且关联了该商品或分类

        return couponMapper.selectByExample(couponExample);
    }


    @Override
    public List<SmsCoupon> list(Integer useStatus) {
        UmsMember member = memberService.getCurrentMember();
        return couponHistoryDao.getCouponList(member.getId(),useStatus);
    }


    /**
     * 计算购物车所有商品的总价（按优惠后价格计算）
     * realPrice = 原价 - 优惠金额
     * total = Σ(realPrice × 数量)
     */
    private BigDecimal calcTotalAmount(List<CartPromotionItem> cartItemList) {
        BigDecimal total = new BigDecimal("0");
        for (CartPromotionItem item : cartItemList) {
            BigDecimal realPrice = item.getPrice().subtract(item.getReduceAmount());
            total = total.add(realPrice.multiply(new BigDecimal(item.getQuantity())));
        }
        return total;
    }

    /**
     * 计算指定分类商品的总价
     * 用于判断"指定分类"优惠券是否满足满减门槛
     */
    private BigDecimal calcTotalAmountByproductCategoryId(List<CartPromotionItem> cartItemList,
                                                          List<Long> productCategoryIds) {
        BigDecimal total = new BigDecimal("0");
        for (CartPromotionItem item : cartItemList) {
            if (productCategoryIds.contains(item.getProductCategoryId())) {
                BigDecimal realPrice = item.getPrice().subtract(item.getReduceAmount());
                total = total.add(realPrice.multiply(new BigDecimal(item.getQuantity())));
            }
        }
        return total;
    }

    /**
     * 计算指定商品的总价
     * 用于判断"指定商品"优惠券是否满足满减门槛
     */
    private BigDecimal calcTotalAmountByProductId(List<CartPromotionItem> cartItemList,
                                                  List<Long> productIds) {
        BigDecimal total = new BigDecimal("0");
        for (CartPromotionItem item : cartItemList) {
            if (productIds.contains(item.getProductId())) {
                BigDecimal realPrice = item.getPrice().subtract(item.getReduceAmount());
                total = total.add(realPrice.multiply(new BigDecimal(item.getQuantity())));
            }
        }
        return total;
    }



    /**
     * 生成优惠券码
     * 优惠码要解决的是"不可猜测 + 可输入 + 可校验"问题
     */
    private String generateCouponCode(Long memberId) {
        StringBuilder sb = new StringBuilder();

        // ① 时间戳后8位
        Long currentTimeMillis = System.currentTimeMillis();
        String timeMillisStr = currentTimeMillis.toString();
        sb.append(timeMillisStr.substring(timeMillisStr.length() - 8));

        // ② 4位随机数（0-9）
        Random random = new Random();
        for (int i = 0; i < 4; i++) {
            sb.append(random.nextInt(10));
        }

        // ③ 用户id后4位，不足4位前面补0
        String memberIdStr = memberId.toString();
        if (memberIdStr.length() <= 4) {
            sb.append(String.format("%04d", memberId));  // 补0到4位
        } else {
            sb.append(memberIdStr.substring(memberIdStr.length() - 4));  // 取后4位
        }

        return sb.toString();
    }

}
