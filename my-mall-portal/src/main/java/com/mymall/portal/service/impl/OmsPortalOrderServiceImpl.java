package com.mymall.portal.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.mymall.common.api.CommonPage;
import com.mymall.common.exception.Asserts;
import com.mymall.common.redis.RedisService;
import com.mymall.portal.component.CancelOrderSender;
import com.mymall.portal.dao.*;
import com.mymall.portal.domain.*;
import com.mymall.portal.model.*;
import com.mymall.portal.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OmsPortalOrderServiceImpl  implements OmsPortalOrderService {
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private OmsCartItemService cartItemService;
    @Autowired
    private UmsMemberReceiveAddressService memberReceiveAddressService;
    @Autowired
    private UmsMemberCouponService memberCouponService;
    @Autowired
    private UmsIntegrationConsumeSettingMapper integrationConsumeSettingMapper;
    @Autowired
    private PmsSkuStockMapper skuStockMapper;
    @Autowired
    private RedisService redisService;
    @Value("${redis.key.orderId}")
    private String REDIS_KEY_ORDER_ID;
    @Value("${redis.database}")
    private String REDIS_DATABASE;
    @Autowired
    private OmsOrderSettingMapper orderSettingMapper;
    @Autowired
    private OmsOrderMapper orderMapper;
    @Autowired
    private PortalOrderItemDao orderItemDao;
    @Autowired
    private SmsCouponHistoryMapper couponHistoryMapper;
    @Autowired
    private CancelOrderSender cancelOrderSender;
    @Autowired
    private PortalOrderDao portalOrderDao;
    @Autowired
    private OmsOrderItemMapper orderItemMapper;



    /**
     * 生成确认订单页面信息
     * 组装：购物车商品 + 收货地址 + 可用优惠券 + 积分 + 金额计算
     * 这是下单前的最后一步，用户点击"去结算"时调用
     * 注：`generateConfirmOrder` 返回的 `calcAmount.payAmount` 是**商品促销后的金额**，还没减优惠券和积分。
     */
    @Override
    public ConfirmOrderResult generateConfirmOrder(List<Long> cartIds) {
        ConfirmOrderResult result = new ConfirmOrderResult();

        // ① 获取当前登录会员
        UmsMember currentMember = memberService.getCurrentMember();

        // ② 获取购物车促销信息（已算好商品促销：单品/阶梯打折/满减）
        List<CartPromotionItem> cartPromotionItemList =
                cartItemService.listPromotion(currentMember.getId(), cartIds);
        result.setCartPromotionItemList(cartPromotionItemList);

        // ③ 获取用户收货地址列表（用户选择用哪个地址）
        List<UmsMemberReceiveAddress> memberReceiveAddressList =
                memberReceiveAddressService.list();
        result.setMemberReceiveAddressList(memberReceiveAddressList);

        // ④ 获取用户可用优惠券列表（根据购物车判断哪些券可用）
        List<SmsCouponHistoryDetail> couponHistoryDetailList =
                memberCouponService.listCart(cartPromotionItemList, 1);
        result.setCouponHistoryDetailList(couponHistoryDetailList);

        // ⑤ 获取用户积分
        result.setMemberIntegration(currentMember.getIntegration());

        // ⑥ 获取积分使用规则（多少积分抵1元，最多抵多少）
        UmsIntegrationConsumeSetting integrationConsumeSetting =
                integrationConsumeSettingMapper.selectByPrimaryKey(1L);
        result.setIntegrationConsumeSetting(integrationConsumeSetting);

        // ⑦ 计算总金额、活动优惠、应付金额
        ConfirmOrderResult.CalcAmount calcAmount = calcCartAmount(cartPromotionItemList);
        result.setCalcAmount(calcAmount);

        return result;
    }

    /**
     * 生成订单（下单核心方法）
     * 流程：
     * 1. 校验参数（收货地址）
     * 2. 获取购物车促销信息，生成订单明细
     * 3. 校验库存
     * 4. 处理优惠券（按比例分摊到每件商品）
     * 5. 处理积分（按比例分摊到每件商品）
     * 6. 计算每件商品实付金额
     * 7. 锁定库存
     * 8. 计算订单总金额
     * 9. 插入订单表+订单明细表
     * 10. 更新优惠券使用状态
     * 11. 扣除用户积分
     * 12. 删除购物车中已下单商品
     * 13. 发送延迟消息（超时未支付自动取消）
     *
     * TODO 跨模块操作（扣库存、扣优惠券）需要分布式事务
     */
    @Override
    @Transactional
    public Map<String, Object> generateOrder(OrderParam orderParam) {
        List<OmsOrderItem> orderItemList = new ArrayList<>();

        // ========== 1. 校验参数 ==========
        if (orderParam.getMemberReceiveAddressId() == null) {
            Asserts.fail("请选择收货地址！");
        }

        // ========== 2. 获取购物车促销信息，生成订单明细 ==========
        UmsMember currentMember = memberService.getCurrentMember();
        List<CartPromotionItem> cartPromotionItemList =
                cartItemService.listPromotion(currentMember.getId(), orderParam.getCartIds());

        for (CartPromotionItem item : cartPromotionItemList) {
            OmsOrderItem orderItem = new OmsOrderItem();
            // 商品基本信息
            orderItem.setProductId(item.getProductId());
            orderItem.setProductName(item.getProductName());
            orderItem.setProductPic(item.getProductPic());
            orderItem.setProductAttr(item.getProductAttr());
            orderItem.setProductBrand(item.getProductBrand());
            orderItem.setProductSn(item.getProductSn());
            orderItem.setProductPrice(item.getPrice());
            orderItem.setProductQuantity(item.getQuantity());
            orderItem.setProductSkuId(item.getProductSkuId());
            orderItem.setProductSkuCode(item.getProductSkuCode());
            orderItem.setProductCategoryId(item.getProductCategoryId());
            // 商品促销信息
            orderItem.setPromotionAmount(item.getReduceAmount());
            orderItem.setPromotionName(item.getPromotionMessage());
            orderItem.setGiftIntegration(item.getIntegration());
            orderItem.setGiftGrowth(item.getGrowth());
            orderItemList.add(orderItem);
        }

        // ========== 3. 校验库存 ==========
        if (!hasStock(cartPromotionItemList)) {
            Asserts.fail("库存不足，无法下单");
        }

        // ========== 4. 处理优惠券 ==========
        if (orderParam.getCouponId() == null) {
            // 不用优惠券，每件商品优惠券金额=0
            for (OmsOrderItem orderItem : orderItemList) {
                orderItem.setCouponAmount(new BigDecimal(0));
            }
        } else {
            // 使用优惠券：校验券是否可用，按价格比例分摊到每件商品
            SmsCouponHistoryDetail coupon = getUseCoupon(cartPromotionItemList, orderParam.getCouponId());
            if (coupon == null) {
                Asserts.fail("该优惠券不可用");
            }
//            handleCouponAmount 把优惠券金额分摊到每件商品上，写在 orderItem.couponAmount 里，
//             供后续计算和入库使用
            handleCouponAmount(orderItemList, coupon);
        }

        // ========== 5. 处理积分 ==========
        if (orderParam.getUseIntegration() == null || orderParam.getUseIntegration().equals(0)) {
            // 不使用积分
            for (OmsOrderItem orderItem : orderItemList) {
                orderItem.setIntegrationAmount(new BigDecimal(0));
            }
        } else {
            // 使用积分：计算可抵扣金额，按价格比例分摊
            BigDecimal totalAmount = calcTotalAmount(orderItemList);
            BigDecimal integrationAmount = getUseIntegrationAmount(
                    orderParam.getUseIntegration(), totalAmount, currentMember,
                    orderParam.getCouponId() != null);
            if (integrationAmount.compareTo(BigDecimal.ZERO) == 0) {
                Asserts.fail("积分不可用");
            }
            // 按比例分摊到每件商品
            for (OmsOrderItem orderItem : orderItemList) {
                BigDecimal perAmount = orderItem.getProductPrice()
                        .divide(totalAmount, 3, RoundingMode.HALF_EVEN)
                        .multiply(integrationAmount);
                orderItem.setIntegrationAmount(perAmount);
            }
        }

        // ========== 6. 计算每件商品实付金额 ==========
        // realAmount = productPrice - promotionAmount - couponAmount - integrationAmount
        handleRealAmount(orderItemList);

        // ========== 7. 锁定库存 ==========
        // TODO 高并发下需要乐观锁：UPDATE pms_sku_stock SET stock=stock-?, lock_stock=lock_stock+? WHERE id=? AND stock>=?
        lockStock(cartPromotionItemList);

        // ========== 8. 计算订单总金额 ==========
        OmsOrder order = new OmsOrder();
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(calcTotalAmount(orderItemList));           // 商品原价合计
        order.setFreightAmount(BigDecimal.ZERO);                        // 运费
        order.setPromotionAmount(calcPromotionAmount(orderItemList));   // 商品促销优惠
        order.setPromotionInfo(getOrderPromotionInfo(orderItemList));   // 订单促销信息

        if (orderParam.getCouponId() == null) {
            order.setCouponAmount(BigDecimal.ZERO);
        } else {
            order.setCouponId(orderParam.getCouponId());
            order.setCouponAmount(calcCouponAmount(orderItemList));     // 优惠券优惠
        }

        if (orderParam.getUseIntegration() == null) {
            order.setIntegration(0);
            order.setIntegrationAmount(BigDecimal.ZERO);
        } else {
            order.setIntegration(orderParam.getUseIntegration());
            order.setIntegrationAmount(calcIntegrationAmount(orderItemList));  // 积分抵扣
        }

        // 应付金额 = 原价 + 运费 - 商品促销 - 优惠券 - 积分
        order.setPayAmount(calcPayAmount(order));

        // ========== 9. 填充订单其他信息 ==========
        order.setMemberId(currentMember.getId());
        order.setCreateTime(new Date());
        order.setMemberUsername(currentMember.getUsername());
        order.setPayType(orderParam.getPayType());        // 支付方式       0->未支付；1->支付宝；2->微信
        order.setSourceType(1);                           // 订单来源：app   0->PC订单；1->app订单
        order.setStatus(0);                               // 订单状态：待付款  0->待付款；1->待发货；2->已发货；3->已完成；4->已关闭；5->无效订单
        order.setOrderType(0);                            // 订单类型：正常订单 0->正常订单；1->秒杀订单 

        // 收货人信息：姓名、电话、邮编、地址
        UmsMemberReceiveAddress address =
                memberReceiveAddressService.getItem(orderParam.getMemberReceiveAddressId());
        order.setReceiverName(address.getName());
        order.setReceiverPhone(address.getPhoneNumber());
        order.setReceiverPostCode(address.getPostCode());
        order.setReceiverProvince(address.getProvince());
        order.setReceiverCity(address.getCity());
        order.setReceiverRegion(address.getRegion());
        order.setReceiverDetailAddress(address.getDetailAddress());

        order.setConfirmStatus(0); //0->未确认；1->已确认
        order.setDeleteStatus(0);
        order.setIntegration(calcGifIntegration(orderItemList));   // 赠送积分
        order.setGrowth(calcGiftGrowth(orderItemList));            // 赠送成长值
        order.setOrderSn(generateOrderSn(order));                  // 生成订单号

        // 自动确认收货天数
        List<OmsOrderSetting> orderSettings =
                orderSettingMapper.selectByExample(new OmsOrderSettingExample());
        if (CollUtil.isNotEmpty(orderSettings)) {
            order.setAutoConfirmDay(orderSettings.get(0).getConfirmOvertime());
        }

        // ========== 10. 插入订单表+订单明细表 ==========
        orderMapper.insert(order);
        for (OmsOrderItem orderItem : orderItemList) {
            orderItem.setOrderId(order.getId());
            orderItem.setOrderSn(order.getOrderSn());
        }
        orderItemDao.insertList(orderItemList);  // 批量插入明细

        // ========== 11. 更新优惠券使用状态 ==========
        if (orderParam.getCouponId() != null) {
            updateCouponStatus(orderParam.getCouponId(), currentMember.getId(), 1);  // 1=已使用
        }

        // ========== 12. 扣除用户积分 ==========
        if (orderParam.getUseIntegration() != null) {
            order.setUseIntegration(orderParam.getUseIntegration());
            if (currentMember.getIntegration() == null) {
                currentMember.setIntegration(0);
            }
            memberService.updateIntegration(
                    currentMember.getId(), 
                    currentMember.getIntegration() - orderParam.getUseIntegration());
        }

        // ========== 13. 删除购物车中已下单商品 ==========
        deleteCartItemList(cartPromotionItemList, currentMember);

        // ========== 14. 发送延迟消息（超时未支付自动取消） ==========
        // TODO 需要引入RabbitMQ，30分钟未支付自动取消订单、释放库存、返还优惠券
        sendDelayMessageCancelOrder(order.getId());

        // TODO 更规范的做法是定义一个结果 DTO
        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("orderItemList", orderItemList);
        return result;
    }

    /**
     * 支付成功回调处理
     * ① 修改订单状态：待付款(0) → 待发货(1)，记录支付时间和支付方式
     * ② 库存处理：把下单时"锁定"的库存真正扣减（lock_stock减回去）
     *
     * 库存完整流程：
     *   下单锁定：stock - n，lock_stock + n（暂时锁住）
     *   支付成功：lock_stock - n（锁定转真实扣减，stock不变）
     *   取消订单：stock + n，lock_stock - n（释放）
     *   TODO 后期幂等
     * @param orderId 订单ID
     * @param payType 支付方式（1支付宝，2微信）
     * @return 库存更新影响行数
     */
    @Override
    @Transactional
    public Integer paySuccess(Long orderId, Integer payType) {
        // ① 修改订单支付状态
        OmsOrder order = new OmsOrder();
        order.setId(orderId);
        order.setStatus(1);                    // 0待付款 → 1待发货
        order.setPaymentTime(new Date());      // 支付时间
        order.setPayType(payType);             // 支付方式
        orderMapper.updateByPrimaryKeySelective(order);

        // ② 查询订单明细
        OmsOrderDetail orderDetail = portalOrderDao.getDetail(orderId);

        // ③ 恢复锁定库存，扣减真实库存（lock_stock 减回）
        int count = portalOrderDao.updateSkuStock(orderDetail.getOrderItemList());

        return count;
    }

    /**
     * 取消超时未支付订单（定时任务兜底方案，不依赖MQ）
     * 由定时任务周期性调用，扫描超时还没付款的订单，自动取消并释放资源
     *
     * 释放资源三件事：
     *   ① 解除库存锁定（lock_stock减回）
     *   ② 返还优惠券（use_status改回未使用）
     *   ③ 返还使用的积分
     *
     * @return 取消的订单数量
     */
    @Override
    @Transactional
    public Integer cancelTimeOutOrder() {
        Integer count = 0;

        // ① 查询订单设置，获取普通订单超时时间（分钟）
        OmsOrderSetting orderSetting = orderSettingMapper.selectByPrimaryKey(1L);

        // ② 查询超时、未支付的订单及详情
        List<OmsOrderDetail> timeOutOrders =
                portalOrderDao.getTimeOutOrders(orderSetting.getNormalOrderOvertime());

        if (CollectionUtils.isEmpty(timeOutOrders)) {
            return count;   // 没有超时订单，直接返回
        }

        // ③ 收集所有超时订单ID
        List<Long> ids = new ArrayList<>();
        for (OmsOrderDetail timeOutOrder : timeOutOrders) {
            ids.add(timeOutOrder.getId());
        }

        // ④ 批量修改订单状态为"已关闭"(4)
        portalOrderDao.updateOrderStatus(ids, 4);

        // ⑤ 遍历每个订单，逐个释放资源
        for (OmsOrderDetail timeOutOrder : timeOutOrders) {
            // 5.1 解除商品库存锁定（lock_stock减，stock不动）
            portalOrderDao.releaseSkuStockLock(timeOutOrder.getOrderItemList());

            // 5.2 返还优惠券（use_status 改回0未使用）
            updateCouponStatus(timeOutOrder.getCouponId(), timeOutOrder.getMemberId(), 0);

            // 5.3 返还使用的积分
            if (timeOutOrder.getUseIntegration() != null) {
                UmsMember member = memberService.getById(timeOutOrder.getMemberId());
                memberService.updateIntegration(
                        timeOutOrder.getMemberId(),
                        member.getIntegration() + timeOutOrder.getUseIntegration());
            }
        }

        return timeOutOrders.size();
    }



    /**
     * 发送延迟消息取消订单
     * 下单成功后调用，延迟一段时间（订单超时时间）后由消费者检查：
     *   如果订单还是"待付款"状态，自动取消并释放资源
     *
     * @param orderId 订单ID
     */
    @Override
    public void sendDelayMessageCancelOrder(Long orderId) {
        // ① 查询订单设置，获取普通订单超时时间（单位：分钟）
        OmsOrderSetting orderSetting = orderSettingMapper.selectByPrimaryKey(1L);

        // ② 换算成毫秒（分钟 × 60秒 × 1000毫秒）
        long delayTimes = orderSetting.getNormalOrderOvertime() * 60 * 1000;

        // ③ 发送延迟消息（RabbitMQ，消息过期后进入死信队列被消费）
         cancelOrderSender.sendMessage(orderId, delayTimes);
    }

    /**
     * 分页获取用户订单列表（含订单明细）
     *
     * 优化写法：先分页查订单主表，再批量查明细表，Service层组装
     * 避免了 LEFT JOIN 一对多导致的分页不准和笛卡尔积问题
     *
     * @param status   订单状态：-1->全部；0->待付款；1->待发货；2->已发货；3->已完成；4->已关闭
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 分页订单详情列表
     */
    @Override
    public CommonPage<OmsOrderDetail> list(Integer status, Integer pageNum, Integer pageSize) {
        // status=-1 表示全部
        if (status != null && status == -1) {
            status = null;
        }

        UmsMember member = memberService.getCurrentMember();

        // PageHelper 分页（必须紧跟查询）
        PageHelper.startPage(pageNum, pageSize);

        // 查订单
        OmsOrderExample orderExample = new OmsOrderExample();
        OmsOrderExample.Criteria criteria = orderExample.createCriteria();
        criteria.andDeleteStatusEqualTo(0).andMemberIdEqualTo(member.getId());
        if (status != null) {
            criteria.andStatusEqualTo(status);
        }
        orderExample.setOrderByClause("create_time desc");
        List<OmsOrder> orderList = orderMapper.selectByExample(orderExample);

        // 立即提取分页总数（必须在下次查询前）
        long total = new PageInfo<>(orderList).getTotal();

        // 空结果直接返回
        if (CollUtil.isEmpty(orderList)) {
            return CommonPage.of(pageNum, pageSize, total, Collections.emptyList());
        }

        // 批量查明细
        List<Long> orderIds = orderList.stream()
                .map(OmsOrder::getId)
                .collect(Collectors.toList());
        OmsOrderItemExample itemExample = new OmsOrderItemExample();
        itemExample.createCriteria().andOrderIdIn(orderIds);
        List<OmsOrderItem> orderItemList = orderItemMapper.selectByExample(itemExample);

        // 【优化】明细按 orderId 分组到 Map，O(n) 一次完成
        Map<Long, List<OmsOrderItem>> itemMap = orderItemList.stream()
                .collect(Collectors.groupingBy(OmsOrderItem::getOrderId));

        // 组装：每个订单直接从 Map 取，O(1) 查找
        List<OmsOrderDetail> orderDetailList = orderList.stream()
                .map(order -> {
                    OmsOrderDetail detail = new OmsOrderDetail();
                    BeanUtil.copyProperties(order, detail);
                    detail.setOrderItemList(itemMap.getOrDefault(order.getId(), Collections.emptyList()));
                    return detail;
                })
                .collect(Collectors.toList());

        return CommonPage.of(pageNum, pageSize, total, orderDetailList);
    }

    @Override
    public OmsOrderDetail detail(Long orderId) {
        OmsOrder omsOrder = orderMapper.selectByPrimaryKey(orderId);
        OmsOrderItemExample example = new OmsOrderItemExample();
        example.createCriteria().andOrderIdEqualTo(orderId);
        List<OmsOrderItem> orderItemList = orderItemMapper.selectByExample(example);
        OmsOrderDetail orderDetail = new OmsOrderDetail();
        BeanUtil.copyProperties(omsOrder,orderDetail);
        orderDetail.setOrderItemList(orderItemList);
        return orderDetail;
    }

    /**
     * 取消单个订单（立即取消，用户主动取消 / 超时取消都调这个）
     *
     * 释放资源四步：
     *   ① 订单状态：待付款(0) → 已关闭(4)
     *   ② 释放库存锁定（lock_stock减，stock不动）
     *   ③ 返还优惠券（use_status改回未使用）
     *   ④ 返还使用的积分
     *
     *
     * @param orderId 订单ID
     */
    @Override
    @Transactional
    public void cancelOrder(Long orderId) {
        // ① 查询未付款且未删除的订单（只有待付款状态才能取消）
        OmsOrderExample example = new OmsOrderExample();
        example.createCriteria()
                .andIdEqualTo(orderId)
                .andStatusEqualTo(0)        // 待付款
                .andDeleteStatusEqualTo(0); // 未删除
        List<OmsOrder> cancelOrderList = orderMapper.selectByExample(example);

        // 订单不存在或不是待付款状态，直接返回（幂等：已取消/已支付的不处理）
        if (CollectionUtils.isEmpty(cancelOrderList)) {
            return;
        }

        OmsOrder cancelOrder = cancelOrderList.get(0);

        // ② 修改订单状态为已关闭(4)
        cancelOrder.setStatus(4);
        orderMapper.updateByPrimaryKeySelective(cancelOrder);

        // ③ 查询订单明细
        OmsOrderItemExample orderItemExample = new OmsOrderItemExample();
        orderItemExample.createCriteria().andOrderIdEqualTo(orderId);
        List<OmsOrderItem> orderItemList = orderItemMapper.selectByExample(orderItemExample);

        // ④ 释放库存锁定（lock_stock减回，stock不动，因为下单时没减stock）
        if (!CollectionUtils.isEmpty(orderItemList)) {
            portalOrderDao.releaseSkuStockLock(orderItemList);
        }

        // ⑤ 返还优惠券（use_status 改回0未使用）
        updateCouponStatus(cancelOrder.getCouponId(), cancelOrder.getMemberId(), 0);

        // ⑥ 返还使用的积分
        if (cancelOrder.getUseIntegration() != null) {
            UmsMember member = memberService.getById(cancelOrder.getMemberId());
            memberService.updateIntegration(
                    cancelOrder.getMemberId(),
                    member.getIntegration() + cancelOrder.getUseIntegration());
        }
    }

    /**
     * 用户确认收货
     * 订单状态：已发货(2) → 已完成(3)
     *
     * 校验：
     *   ① 只能确认自己的订单
     *   ② 只有已发货的订单才能确认收货
     *
     * @param orderId 订单ID
     */
    @Override
    public void confirmReceiveOrder(Long orderId) {
        // ① 获取当前登录用户
        UmsMember member = memberService.getCurrentMember();

        // ② 查询订单
        OmsOrder order = orderMapper.selectByPrimaryKey(orderId);

        // ③ 校验：只能确认自己的订单（防越权）
        if (!member.getId().equals(order.getMemberId())) {
            Asserts.fail("不能确认他人订单！");
        }

        // ④ 校验：只有已发货(status=2)才能确认收货
        if (order.getStatus() != 2) {
            Asserts.fail("该订单还未发货！");
        }

        // ⑤ 更新订单状态
        order.setStatus(3);              // 已发货 → 已完成
        order.setConfirmStatus(1);       // 已确认
        order.setReceiveTime(new Date()); // 收货时间
        orderMapper.updateByPrimaryKey(order);
    }

    /**
     * 删除订单 已完成(3) 或 已关闭(4) 逻辑删除
     * @param orderId
     */
    @Override
    public void deleteOrder(Long orderId) {
        UmsMember member = memberService.getCurrentMember();
        OmsOrder order = orderMapper.selectByPrimaryKey(orderId);
        if(!member.getId().equals(order.getMemberId())){
            Asserts.fail("不能删除他人订单！");
        }
        if(order.getStatus()==3||order.getStatus()==4){
            order.setDeleteStatus(1);
            orderMapper.updateByPrimaryKey(order);
        }else{
            Asserts.fail("只能删除已完成或已关闭的订单！");
        }
    }


    /**
     * 删除下单商品的购物车信息
     */
    private void deleteCartItemList(List<CartPromotionItem> cartPromotionItemList, UmsMember currentMember) {
        List<Long> ids = new ArrayList<>();
        for (CartPromotionItem cartPromotionItem : cartPromotionItemList) {
            ids.add(cartPromotionItem.getId());
        }
        cartItemService.delete(currentMember.getId(), ids);
    }

    /**
     * 将优惠券信息更改为指定状态
     * 下单时：未使用(0) → 已使用(1)，记录使用时间
     * 取消/超时：已使用(1) → 未使用(0)，清空使用时间
     *
     * @param couponId  优惠券ID
     * @param memberId  会员ID
     * @param useStatus 目标状态：0->未使用；1->已使用
     */
    private void updateCouponStatus(Long couponId, Long memberId, Integer useStatus) {
        if (couponId == null) return;

        // ① 查询当前状态"相反"的领取记录
        SmsCouponHistoryExample example = new SmsCouponHistoryExample();
        example.createCriteria()
                .andMemberIdEqualTo(memberId)
                .andCouponIdEqualTo(couponId)
                .andUseStatusEqualTo(useStatus == 0 ? 1 : 0);

        List<SmsCouponHistory> couponHistoryList =
                couponHistoryMapper.selectByExample(example);

        // ② 找到记录，更新状态
        if (!CollectionUtils.isEmpty(couponHistoryList)) {
            SmsCouponHistory couponHistory = couponHistoryList.get(0);

            if (useStatus.equals(1)) {
                // 下单用券：状态改为已使用，记录使用时间
                couponHistory.setUseStatus(1);
                couponHistory.setUseTime(new Date());
                couponHistoryMapper.updateByPrimaryKeySelective(couponHistory);
            } else {
                // 取消返还：状态改回未使用，清空使用时间
                // 用自定义SQL，因为Selective模式会忽略null、无法清空use_time
                couponHistoryMapper.releaseCoupon(couponId, memberId);
            }
        }
    }


    /**
     * 生成18位订单编号
     * 组成：8位日期 + 2位平台号码 + 2位支付方式 + 6位以上自增id
     * 例如：20261009 + 01 + 01 + 000001 = 202610090101000001
     *
     * 自增id用 Redis incr 生成，保证同一天内订单号唯一且递增
     *
     * @param order 订单（含来源、支付方式）
     * @return 18位订单编号
     */
    private String generateOrderSn(OmsOrder order) {
        StringBuilder sb = new StringBuilder();

        // ① 8位日期
        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());

        // ② Redis自增key，按天区分（每天从1开始）
        String key = REDIS_DATABASE + ":" + REDIS_KEY_ORDER_ID + date;
        Long increment = redisService.incr(key, 1);  // 原子自增

        // ③ 拼接日期
        sb.append(date);
        // ④ 2位平台号码（订单来源），不足2位补0
        sb.append(String.format("%02d", order.getSourceType()));
        // ⑤ 2位支付方式，不足2位补0
        sb.append(String.format("%02d", order.getPayType()));

        // ⑥ 6位以上自增id，不足6位前面补0，超过6位直接用
        String incrementStr = increment.toString();
        if (incrementStr.length() <= 6) {
            sb.append(String.format("%06d", increment));
        } else {
            sb.append(incrementStr);
        }

        return sb.toString();
    }


    /**
     * 计算该订单赠送的成长值
     */
    private Integer calcGiftGrowth(List<OmsOrderItem> orderItemList) {
        Integer sum = 0;
        for (OmsOrderItem orderItem : orderItemList) {
            sum = sum + orderItem.getGiftGrowth() * orderItem.getProductQuantity();
        }
        return sum;
    }

    /**
     * 计算该订单赠送的积分（下单成功后奖励给用户的积分）
     * 公式：赠送积分 = Σ(每件商品赠送积分 × 购买数量)
     *
     * 注意：这是"赠送"积分（买商品赚到的），不是"抵扣"积分（花掉的）
     * 方法名 Gif 是原项目的拼写（应为Gift），保持一致
     *
     * @param orderItemList 订单明细列表（giftIntegration来自商品促销设置）
     * @return 订单赠送积分总数
     */
    private Integer calcGifIntegration(List<OmsOrderItem> orderItemList) {
        int sum = 0;

        for (OmsOrderItem orderItem : orderItemList) {
            // 该商品赠送积分小计 = 每件赠送积分 × 数量
            sum += orderItem.getGiftIntegration() * orderItem.getProductQuantity();
        }

        return sum;
    }


    /**
     * 计算订单应付金额
     */
    private BigDecimal calcPayAmount(OmsOrder order) {
        //总金额+运费-促销优惠-优惠券优惠-积分抵扣
        BigDecimal payAmount = order.getTotalAmount()
                .add(order.getFreightAmount())
                .subtract(order.getPromotionAmount())
                .subtract(order.getCouponAmount())
                .subtract(order.getIntegrationAmount());
        return payAmount;
    }

    /**
     * 计算订单优惠券金额
     */
    private BigDecimal calcIntegrationAmount(List<OmsOrderItem> orderItemList) {
        BigDecimal integrationAmount = new BigDecimal(0);
        for (OmsOrderItem orderItem : orderItemList) {
            if (orderItem.getIntegrationAmount() != null) {
                integrationAmount = integrationAmount.add(orderItem.getIntegrationAmount().multiply(new BigDecimal(orderItem.getProductQuantity())));
            }
        }
        return integrationAmount;
    }

    /**
     * 计算订单优惠券优惠总金额
     * 公式：优惠券合计 = Σ(每件商品优惠券分摊 × 购买数量)
     *
     * @param orderItemList 订单明细列表（couponAmount已在handleCouponAmount时分摊设置）
     * @return 优惠券优惠合计
     * calcCouponAmount 再把分摊结果汇总存到主表方便统计
     */
    private BigDecimal calcCouponAmount(List<OmsOrderItem> orderItemList) {
        BigDecimal couponAmount = BigDecimal.ZERO;

        for (OmsOrderItem orderItem : orderItemList) {
            if (orderItem.getCouponAmount() != null) {
                // 该商品优惠券分摊小计 = 每件分摊 × 数量
                BigDecimal itemCoupon = orderItem.getCouponAmount()
                        .multiply(new BigDecimal(orderItem.getProductQuantity()));
                couponAmount = couponAmount.add(itemCoupon);
            }
        }

        return couponAmount;
    }


    /**
     * 获取订单促销信息（把所有商品的促销名称拼接成一个字符串）
     * 用于订单主表 promotion_info 字段，记录这个订单用了哪些促销
     *
     * @param orderItemList 订单明细列表（promotionName已设置）
     * @return 用分号拼接的促销名称，如 "满10000减800;单品促销"
     */
    private String getOrderPromotionInfo(List<OmsOrderItem> orderItemList) {
        StringBuilder sb = new StringBuilder();

        // 遍历每件商品，拼接促销名称，后面加分号
        for (OmsOrderItem orderItem : orderItemList) {
            sb.append(orderItem.getPromotionName());
            sb.append(";");
        }

        String result = sb.toString();

        // 去掉最后多余的分号
        if (result.endsWith(";")) {
            result = result.substring(0, result.length() - 1);
        }

        return result;
    }


    /**
     * 计算订单的商品促销优惠总金额
     * 公式：促销优惠合计 = Σ(每件商品促销优惠 × 购买数量)
     * 这里的促销是商品本身的促销（单品促销/阶梯打折/满减），不是优惠券
     *
     * @param orderItemList 订单明细列表（promotionAmount已在购物车促销时设置）
     * @return 商品促销优惠合计
     */
    private BigDecimal calcPromotionAmount(List<OmsOrderItem> orderItemList) {
        BigDecimal promotionAmount = BigDecimal.ZERO;

        for (OmsOrderItem orderItem : orderItemList) {
            if (orderItem.getPromotionAmount() != null) {
                // 该商品促销优惠小计 = 每件优惠 × 数量
                BigDecimal itemPromotion = orderItem.getPromotionAmount()
                        .multiply(new BigDecimal(orderItem.getProductQuantity()));
                promotionAmount = promotionAmount.add(itemPromotion);
            }
        }

        return promotionAmount;
    }


    /**
     * 锁定下单商品的所有库存
     * 把购买数量从 stock 转到 lock_stock（锁定库存）
     *   stock：可售库存
     *   lock_stock：已锁定库存（用户下单未支付，暂时锁住）
     *
     * 用户支付后：lock_stock 减少（真正扣掉）
     * 用户取消/超时：lock_stock 减少，stock 加回去（释放）
     *
     * @param cartPromotionItemList 购物车促销商品列表
     * TODO解决防卖超问题，利用InnnoDB底层update行级锁，实现
     */
    private void lockStock(List<CartPromotionItem> cartPromotionItemList) {
        for (CartPromotionItem item : cartPromotionItemList) {
            // ① 查出SKU当前库存
            PmsSkuStock skuStock = skuStockMapper.selectByPrimaryKey(item.getProductSkuId());

            // ② 锁定库存加上购买数量
            skuStock.setLockStock(skuStock.getLockStock() + item.getQuantity());

            // ③ 更新
            skuStockMapper.updateByPrimaryKeySelective(skuStock);
        }
    }


    /**
     * 计算每件商品的实付金额（每件商品最终付多少钱）
     * 公式：实付金额 = 原价 - 商品促销优惠 - 优惠券分摊 - 积分分摊
     *
     * 注意：这里算的是每件商品的实付单价，不是小计
     * 小计 = realAmount × quantity
     *
     * @param orderItemList 订单明细列表（会被原地修改）
     */
    private void handleRealAmount(List<OmsOrderItem> orderItemList) {
        for (OmsOrderItem orderItem : orderItemList) {
            // 原价
            BigDecimal realAmount = orderItem.getProductPrice()
                    // 减商品促销优惠（单品/阶梯打折/满减）
                    .subtract(orderItem.getPromotionAmount())
                    // 减优惠券分摊金额
                    .subtract(orderItem.getCouponAmount())
                    // 减积分分摊金额
                    .subtract(orderItem.getIntegrationAmount());

            // 设置到订单明细
            orderItem.setRealAmount(realAmount);
        }
    }


    /**
     * 获取可用积分抵扣金额（校验积分是否可用，并算出能抵多少钱）
     *
     * 积分规则（ums_integration_consume_setting 表）：
     *   useUnit：多少积分抵1元（如100积分=1元）
     *   couponStatus：能否和优惠券共用（0不能，1能）
     *   maxPercentPerOrder：积分最多抵订单金额的百分之几（如5%）
     *
     * 校验顺序：
     *   ① 用户积分够不够
     *   ② 积分能不能和优惠券共用
     *   ③ 是否达到最低使用积分门槛
     *   ④ 是否超过订单最高抵扣百分比
     *
     * @param useIntegration 用户想使用的积分数量
     * @param totalAmount    订单总金额（原价）
     * @param currentMember  当前登录用户
     * @param hasCoupon      是否已经使用优惠券
     * @return 积分可抵扣金额，返回0表示积分不可用
     */
    private BigDecimal getUseIntegrationAmount(Integer useIntegration,
                                               BigDecimal totalAmount,
                                               UmsMember currentMember,
                                               boolean hasCoupon) {
        BigDecimal zeroAmount = BigDecimal.ZERO;

        // ① 判断用户是否有这么多积分
        // useIntegration > 用户实际积分 → 积分不够
        if (useIntegration.compareTo(currentMember.getIntegration()) > 0) {
            return zeroAmount;
        }

        // ② 查询积分使用规则
        UmsIntegrationConsumeSetting setting =
                integrationConsumeSettingMapper.selectByPrimaryKey(1L);

        // 如果已经用了优惠券，但规则设置"不可与优惠券共用"（couponStatus=0）
        if (hasCoupon && setting.getCouponStatus().equals(0)) {
            return zeroAmount;
        }

        // ③ 是否达到最低使用积分门槛（如最少100积分才能用）
        if (useIntegration.compareTo(setting.getUseUnit()) < 0) {
            return zeroAmount;
        }

        // ④ 计算积分能抵多少钱
        // 抵扣金额 = 使用积分 ÷ 抵扣单位（100积分=1元 → 500积分=5元）
        BigDecimal integrationAmount = new BigDecimal(useIntegration)
                .divide(new BigDecimal(setting.getUseUnit()), 2, RoundingMode.HALF_EVEN);

        // 计算订单最高可抵扣金额 = 订单总价 × 最高百分比
        // maxPercent = maxPercentPerOrder / 100（5 → 0.05）
        BigDecimal maxPercent = new BigDecimal(setting.getMaxPercentPerOrder())
                .divide(new BigDecimal(100), 2, RoundingMode.HALF_EVEN);

        // 如果积分抵扣金额 > 订单最高可抵扣金额 → 不允许（积分抵太多了）
        if (integrationAmount.compareTo(totalAmount.multiply(maxPercent)) > 0) {
            return zeroAmount;
        }

        // 所有校验通过，返回积分抵扣金额
        return integrationAmount;
    }


    /**
     * 计算订单商品的原价总金额
     * 公式：总价 = Σ(商品原价 × 购买数量)
     * 注意：算的是原价，不减任何优惠
     *
     * @param orderItemList 订单明细列表
     * @return 商品原价合计
     */
    private BigDecimal calcTotalAmount(List<OmsOrderItem> orderItemList) {
        BigDecimal totalAmount = new BigDecimal("0");  // 初始为0

        for (OmsOrderItem item : orderItemList) {
            // 每件商品小计 = 商品原价 × 购买数量
            BigDecimal itemTotal = item.getProductPrice()
                    .multiply(new BigDecimal(item.getProductQuantity()));
            // 累加到总价
            totalAmount = totalAmount.add(itemTotal);
        }

        return totalAmount;
    }


    /**
     * 对优惠券优惠进行处理（入口方法）
     * 根据优惠券类型，决定哪些商品参与优惠分摊：
     *   useType=0 全场通用 → 所有商品都参与
     *   useType=1 指定分类 → 筛选出指定分类的商品参与
     *   useType=2 指定商品 → 筛选出指定商品参与
     * 然后调用 calcPerCouponAmount 按价格比例分摊到每件商品
     *
     * 为什么要分摊而不是直接总价减？
     *   因为订单明细(oms_order_item)需要记录每件商品的优惠金额，
     *   后续退款时才能算出每件商品应退多少钱。
     *
     * @param orderItemList       订单明细列表（所有商品，会被原地修改）
     * @param couponHistoryDetail 可用优惠券详情（含优惠金额、使用类型、关联分类/商品）
     */
    private void handleCouponAmount(List<OmsOrderItem> orderItemList,
                                    SmsCouponHistoryDetail couponHistoryDetail) {
        SmsCoupon coupon = couponHistoryDetail.getCoupon();

        if (coupon.getUseType().equals(0)) {
            // 0->全场通用：所有商品都参与优惠分摊
            calcPerCouponAmount(orderItemList, coupon);

        } else if (coupon.getUseType().equals(1)) {
            // 1->指定分类：先筛选出指定分类的商品，只有这些商品参与分摊
            // type=0 表示按分类筛选
            List<OmsOrderItem> couponOrderItemList =
                    getCouponOrderItemByRelation(couponHistoryDetail, orderItemList, 0);
            calcPerCouponAmount(couponOrderItemList, coupon);

        } else if (coupon.getUseType().equals(2)) {
            // 2->指定商品：先筛选出指定商品，只有这些商品参与分摊
            // type=1 表示按商品筛选
            List<OmsOrderItem> couponOrderItemList =
                    getCouponOrderItemByRelation(couponHistoryDetail, orderItemList, 1);
            calcPerCouponAmount(couponOrderItemList, coupon);
        }
    }

    /**
     * 获取与优惠券有关系的下单商品（筛选出参与优惠分摊的商品）
     * type=0：按分类筛选，商品分类在优惠券关联分类列表中才参与
     * type=1：按商品筛选，商品ID在优惠券关联商品列表中才参与
     *
     * 不参与的商品会被设置 couponAmount=0（不分摊优惠）
     *
     * @param couponHistoryDetail 优惠券详情（含关联分类/商品列表）
     * @param orderItemList       所有下单商品
     * @param type                筛选类型：0->按分类；1->按商品
     * @return 参与优惠分摊的商品列表
     */
    private List<OmsOrderItem> getCouponOrderItemByRelation(
            SmsCouponHistoryDetail couponHistoryDetail,
            List<OmsOrderItem> orderItemList,
            int type) {

        List<OmsOrderItem> result = new ArrayList<>();

        if (type == 0) {
            // 按分类筛选：收集优惠券关联的所有分类ID
            List<Long> categoryIdList = new ArrayList<>();
            for (SmsCouponProductCategoryRelation relation : couponHistoryDetail.getCategoryRelationList()) {
                categoryIdList.add(relation.getProductCategoryId());
            }
            // 遍历订单商品，分类匹配的加入结果，不匹配的 couponAmount 设为0
            for (OmsOrderItem orderItem : orderItemList) {
                if (categoryIdList.contains(orderItem.getProductCategoryId())) {
                    result.add(orderItem);  // 参与分摊
                } else {
                    orderItem.setCouponAmount(BigDecimal.ZERO);  // 不参与，优惠为0
                }
            }

        } else if (type == 1) {
            // 按商品筛选：收集优惠券关联的所有商品ID
            List<Long> productIdList = new ArrayList<>();
            for (SmsCouponProductRelation relation : couponHistoryDetail.getProductRelationList()) {
                productIdList.add(relation.getProductId());
            }
            // 遍历订单商品，商品ID匹配的加入结果，不匹配的 couponAmount 设为0
            for (OmsOrderItem orderItem : orderItemList) {
                if (productIdList.contains(orderItem.getProductId())) {
                    result.add(orderItem);  // 参与分摊
                } else {
                    orderItem.setCouponAmount(BigDecimal.ZERO);  // 不参与，优惠为0
                }
            }
        }

        return result;
    }

    /**
     * 对参与优惠的商品按价格比例分摊优惠券金额
     * 公式：每件商品分摊 = (商品价格 / 参与商品总价) × 优惠券面额
     * 分摊结果设置到 orderItem.couponAmount
     *
     * 举例：优惠券500元，参与商品总价10000
     *   商品A 6000元 → 6000/10000 × 500 = 300元
     *   商品B 4000元 → 4000/10000 × 500 = 200元
     *
     * @param orderItemList 参与优惠分摊的商品列表（会被原地修改）
     * @param coupon        优惠券（含优惠金额）
     */
    private void calcPerCouponAmount(List<OmsOrderItem> orderItemList, SmsCoupon coupon) {
        // ① 计算参与分摊商品的总价
        BigDecimal totalAmount = calcTotalAmount(orderItemList);

        // ② 遍历每件商品，按价格比例分摊
        for (OmsOrderItem orderItem : orderItemList) {
            // 分摊金额 = (商品价格 / 总价) × 优惠券面额
            // divide 保留3位小数，银行家舍入（四舍六入五成双）
            BigDecimal couponAmount = orderItem.getProductPrice()
                    .divide(totalAmount, 3, RoundingMode.HALF_EVEN)
                    .multiply(coupon.getAmount());
            orderItem.setCouponAmount(couponAmount);
        }
    }

    /**
     * 获取用户选择的优惠券，并验证是否可用
     * 先查出购物车所有可用优惠券，再看用户选的这张在不在可用列表里
     * @param cartPromotionItemList 购物车促销信息
     * @param couponId 用户选择的优惠券ID
     * @return 优惠券详情，null=该优惠券不可用（已过期/不满足门槛/已使用）
     * ① 调用 memberCouponService.listCart(cartList, 1)
     *    → 查出当前用户所有可用优惠券：
     *    [
     *      {coupon:{id:1, name:"满1000减100", amount:100, useType:0}},
     *      {coupon:{id:5, name:"全场满10000减500", amount:500, useType:0}},
     *      {coupon:{id:7, name:"手机分类满5000减200", amount:200, useType:1}}
     *    ]
     *
     * ② Stream 过滤 coupon.id == 5
     *    → 找到 {coupon:{id:5, name:"全场满10000减500", amount:500}}
     *
     * ③ findFirst().orElse(null)
     *    → 返回该优惠券详情
     */
    private SmsCouponHistoryDetail getUseCoupon(List<CartPromotionItem> cartPromotionItemList, Long couponId) {

        return memberCouponService.listCart(cartPromotionItemList,1)    // ① 查出所有可用优惠券
                .stream()                                                       // ② 转成Stream流
                .filter(coupon->coupon.getCoupon().getId().equals(couponId))// ③ 过滤出id匹配的券
                .findFirst()                                                            // ④ 取第一个（找到就返回）
                .orElse(null);                                              // ⑤ 没找到返回null
    }

    /**
     * 判断下单商品是否都有库存
     * 遍历购物车中每件商品，检查真实库存是否足够
     * realStock = 总库存 - 已锁定库存（其他用户下单未支付锁定的）
     * @return true=都有库存，false=至少一件库存不足
     */
    private boolean hasStock(List<CartPromotionItem> cartPromotionItemList) {
        for (CartPromotionItem item : cartPromotionItemList) {
            if (item.getRealStock() == null              // 真实库存为空
                    || item.getRealStock() <= 0          // 真实库存<=0（卖完了）
                    || item.getRealStock() < item.getQuantity()) {  // 真实库存 < 购买数量
                return false;
            }
        }
        return true;
    }



    /**
     * 计算购物车中商品的价格
     * 计算三项：
     * - totalAmount：商品原价合计
     * - promotionAmount：商品促销优惠合计
     * - payAmount：应付金额 = 原价合计 - 促销优惠
     * 注意：这里只算商品促销，优惠券和积分抵扣在用户选择后再算
     */
    private ConfirmOrderResult.CalcAmount calcCartAmount(List<CartPromotionItem> cartPromotionItemList) {
        ConfirmOrderResult.CalcAmount calcAmount = new ConfirmOrderResult.CalcAmount();

        // 运费默认0（后续可根据收货地址和金额计算是否包邮）
        calcAmount.setFreightAmount(new BigDecimal(0));

        BigDecimal totalAmount = new BigDecimal("0");      // 原价合计
        BigDecimal promotionAmount = new BigDecimal("0");  // 促销优惠合计

        for (CartPromotionItem item : cartPromotionItemList) {
            // 原价累加：原价 × 数量
            totalAmount = totalAmount.add(
                    item.getPrice().multiply(new BigDecimal(item.getQuantity()))
            );
            // 优惠累加：每件优惠 × 数量
            promotionAmount = promotionAmount.add(
                    item.getReduceAmount().multiply(new BigDecimal(item.getQuantity()))
            );
        }

        calcAmount.setTotalAmount(totalAmount);
        calcAmount.setPromotionAmount(promotionAmount);
        calcAmount.setPayAmount(totalAmount.subtract(promotionAmount));  // 应付 = 原价 - 优惠

        return calcAmount;
    }



}
