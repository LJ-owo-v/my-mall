package com.mymall.portal.service;

import com.mymall.common.api.CommonPage;
import com.mymall.portal.domain.ConfirmOrderResult;
import com.mymall.portal.domain.OmsOrderDetail;
import com.mymall.portal.domain.OrderParam;
import com.mymall.portal.domain.ReconcileResult;

import java.util.List;
import java.util.Map;

public interface OmsPortalOrderService {
    ConfirmOrderResult generateConfirmOrder(List<Long> cartIds);

    Map<String, Object> generateOrder(OrderParam orderParam);

    Integer paySuccess(Long orderId, Integer payType);

    Integer cancelTimeOutOrder();

    void sendDelayMessageCancelOrder(Long orderId);

    CommonPage<OmsOrderDetail> list(Integer status, Integer pageNum, Integer pageSize);

    OmsOrderDetail detail(Long orderId);

    void cancelOrder(Long orderId);

    void confirmReceiveOrder(Long orderId);

    void deleteOrder(Long orderId);

    void paySuccessByOrderSn(String outTradeNo, Integer payType);

    /**
     * 校验支付宝回调金额与订单应付金额是否一致（修复 #16 资损缺口）。
     * <p>
     * 原实现拿到 {@code out_trade_no} 后直接标记已支付，<b>从不比对金额</b> ——
     * 理论上可用 0.01 元的支付结掉 3699 元的订单。
     *
     * @param orderSn     商户订单号（回调参数 out_trade_no）
     * @param totalAmount 支付宝回调的实付金额字符串（回调参数 total_amount，单位：元）
     * @return true=一致（可继续处理）；false=订单不存在 / 金额缺失 / 金额不一致（应拒绝并告警）
     */
    boolean checkOrderAmount(String orderSn, String totalAmount);

    /**
     * 数据对账（M5 #12）：检查 5 条业务不变量，返回每条的结果。
     * <p>
     * 把"我怎么知道数据是对的"从<b>假设</b>变成<b>可验证</b>：
     * 声明一组业务上必须成立的关系，然后核对 —— 不关心问题是怎么产生的，只看最终状态是否自洽。
     * <p>
     * 与日志的区别：日志是"点的、被动的、只覆盖代码走到过的地方"；
     * 对账是"面的、主动的、覆盖任何来源（含历史遗留与手工操作）"。
     * <p>
     * <b>只读</b>：不修改任何数据（自动修复风险高，需人工确认）。
     *
     * @return 5 条不变量各自的检查结果（hitCount=0 即 PASS）
     */
    List<ReconcileResult> reconcile();
}
