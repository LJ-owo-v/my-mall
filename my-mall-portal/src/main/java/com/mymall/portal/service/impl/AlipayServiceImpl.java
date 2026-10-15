package com.mymall.portal.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.mymall.portal.config.AlipayConfig;
import com.mymall.portal.domain.AlipayParam;
import com.mymall.portal.service.AlipayService;
import com.mymall.portal.service.OmsCartItemService;
import com.mymall.portal.service.OmsPortalOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class AlipayServiceImpl implements AlipayService {
    @Autowired
    private AlipayConfig alipayConfig;
    @Autowired
    private AlipayClient alipayClient;
    @Autowired
    private OmsPortalOrderService portalOrderService;


    /**
     * 电脑支付
     * @param aliPayParam
     * @return
     */
    @Override
    public String pay(AlipayParam aliPayParam) {
        // 【项目业务】方法签名、入参类 AliPayParam 都是自己定义的

        // ①【SDK】AlipayTradePagePayRequest 是 SDK 提供的电脑网站支付请求对象
        //    不同支付场景用不同的 Request 类（手机支付用 AlipayTradeWapPayRequest）
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();

        // ②【SDK】request.setNotifyUrl() 是 SDK 提供的方法
        //   【项目业务】alipayConfig 是自己写的配置类，notifyUrl 地址自己定
        //    异步回调地址：支付成功后支付宝服务器主动 POST 这个地址，必须公网可访问
        if (StrUtil.isNotEmpty(alipayConfig.getNotifyUrl())) {
            request.setNotifyUrl(alipayConfig.getNotifyUrl());
        }

        // ③【SDK】request.setReturnUrl() 是 SDK 提供的方法
        //   【项目业务】returnUrl 地址自己定
        //    同步跳转地址：用户付完款后浏览器跳回这个页面，仅用于展示，不能作为支付依据
        if (StrUtil.isNotEmpty(alipayConfig.getReturnUrl())) {
            request.setReturnUrl(alipayConfig.getReturnUrl());
        }

        // ④【第三方库】JSONObject 是 fastjson 提供的（不是支付宝SDK，但也是第三方库）
        JSONObject bizContent = new JSONObject();

        // 【SDK协议】字段名 out_trade_no / total_amount / subject 是支付宝规定的，不能改
        // 【项目业务】字段的值来自自己的入参对象，用哪个订单号、哪个金额自己定
        bizContent.put("out_trade_no", aliPayParam.getOutTradeNo());   // 商户订单号
        bizContent.put("total_amount", aliPayParam.getTotalAmount());  // 支付金额（元）
        bizContent.put("subject", aliPayParam.getSubject());           // 订单标题

        // 【SDK协议】product_code 固定值 FAST_INSTANT_TRADE_PAY，电脑支付必须传这个，不能改
        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");

        // 【SDK】request.setBizContent() 是 SDK 提供的方法，把 JSON 字符串设进请求
        request.setBizContent(bizContent.toString());

        String formHtml = null;  // 【项目业务】局部变量
        try {
            // ⑤【SDK】alipayClient.pageExecute(request) 是 SDK 核心方法
            //    内部自动完成：用应用私钥签名 → 发HTTP到支付宝网关 → 解析响应
            //    pageExecute 是"页面支付"专用，返回 HTML 表单（自动提交到收银台）
            //   【SDK】getBody() 取响应体内容
            formHtml = alipayClient.pageExecute(request).getBody();
        } catch (AlipayApiException e) {  // 【SDK】AlipayApiException 是 SDK 提供的异常类
            // 【项目业务】异常处理方式自己定（打日志/抛异常/返回什么）
            e.printStackTrace();
        }

        // ⑥【项目业务】返回 HTML 给前端，由 Controller 写进 response
        return formHtml;
    }

    /**
     * 移动支付
     * @param aliPayParam
     * @return
     */
    @Override
    public String webPay(AlipayParam aliPayParam) {
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest ();
        if(StrUtil.isNotEmpty(alipayConfig.getNotifyUrl())){
            //异步接收地址，公网可访问
            request.setNotifyUrl(alipayConfig.getNotifyUrl());
        }
        if(StrUtil.isNotEmpty(alipayConfig.getReturnUrl())){
            //同步跳转地址
            request.setReturnUrl(alipayConfig.getReturnUrl());
        }
        //******必传参数******
        com.alibaba.fastjson.JSONObject bizContent = new com.alibaba.fastjson.JSONObject();
        //商户订单号，商家自定义，保持唯一性
        bizContent.put("out_trade_no", aliPayParam.getOutTradeNo());
        //支付金额，最小值0.01元
        bizContent.put("total_amount", aliPayParam.getTotalAmount());
        //订单标题，不可使用特殊符号
        bizContent.put("subject", aliPayParam.getSubject());
        //手机网站支付默认传值FAST_INSTANT_TRADE_PAY
        bizContent.put("product_code", "QUICK_WAP_WAY");
        request.setBizContent(bizContent.toString());
        String formHtml = null;
        try {
            formHtml = alipayClient.pageExecute(request).getBody();
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        return formHtml;
    }

    @Override
    public String notify(Map<String, String> params) {
        // 【项目业务】默认返回 failure，只有验签通过且支付成功才改 success
        String result = "failure";
        boolean signVerified = false;

        try {
            // 【SDK】AlipaySignature.rsaCheckV1() 是 SDK 提供的验签方法
            //   参数1：params —— 支付宝回调的所有参数（含 sign 字段）
            //   参数2：alipayPublicKey —— 支付宝公钥（用来验证签名确实来自支付宝）
            //   参数3：charset —— 编码 UTF-8
            //   参数4：signType —— 签名算法 RSA2
            //   原理：用支付宝公钥解密 params 里的 sign，和 params 其他字段拼起来的字符串对比，一致则验签通过
            signVerified = AlipaySignature.rsaCheckV1(
                    params,
                    alipayConfig.getAlipayPublicKey(),
                    alipayConfig.getCharset(),
                    alipayConfig.getSignType());
        } catch (AlipayApiException e) {  // 【SDK】AlipayApiException 是 SDK 异常
            // 【项目业务】异常处理方式自己定
            log.error("支付回调签名校验异常！", e);
            e.printStackTrace();
        }

        // 【项目业务】验签通过后才处理，不通过直接返回 failure
        if (signVerified) {
            // 【SDK协议】trade_status 是支付宝规定的字段名，不能改
            String tradeStatus = params.get("trade_status");

            // 【SDK协议】TRADE_SUCCESS 是支付宝规定的"支付成功"状态值，不能改
            if ("TRADE_SUCCESS".equals(tradeStatus)) {
                // 【SDK协议】out_trade_no 是支付宝规定的商户订单号字段
                String outTradeNo = params.get("out_trade_no");
                // 【SDK协议】total_amount 是支付宝规定的"订单实际支付金额"字段（单位：元）
                String totalAmount = params.get("total_amount");

                // 【项目业务】【#16 资损缺口修复】先比对金额，不一致直接拒绝：
                //   原实现只按 out_trade_no 找单就标记已支付，从不读 total_amount，
                //   理论上可用 0.01 元的支付结掉 3699 元的订单。
                //   校验失败时返回 failure（让支付宝重试/人工介入），绝不改单。
                if (!portalOrderService.checkOrderAmount(outTradeNo, totalAmount)) {
                    log.error("★ 支付回调金额校验未通过，拒绝处理！out_trade_no={}, total_amount={}",
                            outTradeNo, totalAmount);
                    return "failure";
                }

                // 【项目业务】只有验签通过、且金额一致才返回 success
                result = "success";
                log.info("notify方法被调用了，tradeStatus:{}, out_trade_no:{}, total_amount:{}",
                        tradeStatus, outTradeNo, totalAmount);

                // 【项目业务】调用已做幂等的 paySuccessByOrderSn，更新订单状态、扣库存
                portalOrderService.paySuccessByOrderSn(outTradeNo, 1);
            } else {
                // 其他状态：WAIT_BUYER_PAY(等待付款)、TRADE_CLOSED(交易关闭)、TRADE_FINISHED(交易结束)
                log.warn("订单未支付成功，trade_status:{}", tradeStatus);
            }
        } else {
            // 验签失败：可能是伪造请求，直接拒绝
            log.warn("支付回调签名校验失败！");
        }

        // 【SDK协议】必须返回纯文本 "success" 或 "failure"
        // 返回 success → 支付宝停止重试
        // 返回 failure → 支付宝会按策略重试（最多24小时）
        return result;
    }

    @Override
    public String query(String outTradeNo, String tradeNo) {
        // 【SDK】AlipayTradeQueryRequest 是 SDK 提供的交易查询请求对象
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();

        // 【第三方库】JSONObject 是 fastjson
        JSONObject bizContent = new JSONObject();

        // 【SDK协议】out_trade_no（商户订单号）和 trade_no（支付宝交易号）至少传一个
        // 【项目业务】传哪个值由调用方决定
        if (StrUtil.isNotEmpty(outTradeNo)) {
            bizContent.put("out_trade_no", outTradeNo);
        }
        if (StrUtil.isNotEmpty(tradeNo)) {
            bizContent.put("trade_no", tradeNo);
        }

        // 【SDK协议】query_options 指定要返回的额外信息
        // trade_settle_info = 交易结算信息（退款时的入账明细等）
        String[] queryOptions = {"trade_settle_info"};
        bizContent.put("query_options", queryOptions);

        // 【SDK】把 JSON 设进请求
        request.setBizContent(bizContent.toString());

        AlipayTradeQueryResponse response = null;
        try {
            // 【SDK】alipayClient.execute(request) 是 SDK 核心方法
            //    和 pageExecute 不同：execute 发 HTTP 请求到支付宝，拿 JSON 响应，解析成 Response 对象
            //    pageExecute 生成 HTML 表单（不发HTTP，由浏览器提交）
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {  // 【SDK】异常类
            log.error("查询支付宝账单异常！", e);
        }

        // 【项目业务】【#16 修复】补判空：
        //   上面 catch 分支只记日志、response 仍为 null，若直接调用 response.isSuccess()
        //   会抛 NPE —— 而这恰好发生在"支付宝接口异常"时，即最需要兜底的时刻。
        if (response == null) {
            log.error("查询支付宝账单返回为空（SDK 调用异常），无法判断交易状态. outTradeNo={}, tradeNo={}",
                    outTradeNo, tradeNo);
            return null;
        }

        // 【SDK】response.isSuccess() 判断支付宝接口调用是否成功（不是支付成功）
        if (response.isSuccess()) {
            log.info("查询支付宝账单成功！");
            // 【SDK协议】response.getTradeStatus() 取交易状态
            // 【SDK协议】TRADE_SUCCESS 是支付成功状态
            if ("TRADE_SUCCESS".equals(response.getTradeStatus())) {
                // 【项目业务】查到支付成功，也要比对金额后再调 paySuccess（notify 回调丢失时的兜底）
                //   query 场景下金额取自查询响应，与回调同样需要校验，避免绕过金额校验
                String queriedAmount = response.getTotalAmount();
                if (!portalOrderService.checkOrderAmount(outTradeNo, queriedAmount)) {
                    log.error("★ 查询到的交易金额校验未通过，拒绝处理！outTradeNo={}, totalAmount={}",
                            outTradeNo, queriedAmount);
                } else {
                    portalOrderService.paySuccessByOrderSn(outTradeNo, 1);
                }
            }
        } else {
            // 【SDK】response.getSubCode() / getSubMsg() 可拿具体错误原因
            log.error("查询支付宝账单失败！");
        }

        // 【SDK协议】返回交易状态字符串
        // WAIT_BUYER_PAY：等待付款 | TRADE_CLOSED：已关闭 | TRADE_SUCCESS：支付成功 | TRADE_FINISHED：交易结束
        return response.getTradeStatus();
    }


}
