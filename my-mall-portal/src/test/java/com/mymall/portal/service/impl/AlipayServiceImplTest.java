package com.mymall.portal.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.mymall.portal.config.AlipayConfig;
import com.mymall.portal.domain.AlipayParam;
import com.mymall.portal.service.OmsPortalOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AlipayServiceImpl 纯单元测试。
 *
 * <p>难点：验签是支付宝 SDK 的静态方法 {@code AlipaySignature.rsaCheckV1}。
 * Spring Boot 3.5 带的 Mockito 5 默认 inline mock maker，可以直接 mockStatic，
 * 因此这里不跳过用例，而是把"验签通过/失败"作为前置条件注入。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlipayServiceImpl")
class AlipayServiceImplTest {

    @Mock private AlipayClient alipayClient;
    @Mock private OmsPortalOrderService portalOrderService;

    private AlipayConfig alipayConfig;

    private AlipayServiceImpl alipayService;

    @BeforeEach
    void setUp() {
        alipayConfig = new AlipayConfig();
        alipayConfig.setAlipayPublicKey("test-public-key");
        alipayConfig.setCharset("UTF-8");
        alipayConfig.setSignType("RSA2");
        // AlipayConfig 是普通 POJO：用真实对象比 mock 更接近生产取值链路，
        // 因此不走 @InjectMocks，手动装配三个字段依赖
        alipayService = new AlipayServiceImpl();
        ReflectionTestUtils.setField(alipayService, "alipayConfig", alipayConfig);
        ReflectionTestUtils.setField(alipayService, "alipayClient", alipayClient);
        ReflectionTestUtils.setField(alipayService, "portalOrderService", portalOrderService);
    }

    @Test
    @DisplayName("notify_验签失败_返回failure且不触碰订单")
    void notify_验签失败_返回failure且不触碰订单() {
        try (MockedStatic<AlipaySignature> signature = mockStatic(AlipaySignature.class)) {
            signature.when(() -> AlipaySignature.rsaCheckV1(
                    org.mockito.ArgumentMatchers.anyMap(),
                    anyString(), anyString(), anyString())).thenReturn(false);

            String result = alipayService.notify(params("SN202610270001", "3699.00"));

            // 验签不过 → 可能是伪造回调，必须直接拒绝且不查单
            assertThat(result).isEqualTo("failure");
            verify(portalOrderService, never()).checkOrderAmount(anyString(), anyString());
            verify(portalOrderService, never()).paySuccessByOrderSn(anyString(), anyInt());
        }
    }

    @Test
    @DisplayName("notify_回调缺少total_amount_返回failure")
    void notify_回调缺少total_amount_返回failure() {
        try (MockedStatic<AlipaySignature> signature = mockStatic(AlipaySignature.class)) {
            signature.when(() -> AlipaySignature.rsaCheckV1(
                    org.mockito.ArgumentMatchers.anyMap(),
                    anyString(), anyString(), anyString())).thenReturn(true);
            // 金额缺失 → checkOrderAmount 必然判 false
            when(portalOrderService.checkOrderAmount("SN202610270001", null)).thenReturn(false);

            String result = alipayService.notify(params("SN202610270001", null));

            assertThat(result).isEqualTo("failure");
            verify(portalOrderService).checkOrderAmount("SN202610270001", null);
            verify(portalOrderService, never()).paySuccessByOrderSn(anyString(), anyInt());
        }
    }

    @Test
    @DisplayName("notify_金额与订单不符_返回failure且不改单")
    void notify_金额与订单不符_返回failure且不改单() {
        try (MockedStatic<AlipaySignature> signature = mockStatic(AlipaySignature.class)) {
            signature.when(() -> AlipaySignature.rsaCheckV1(
                    org.mockito.ArgumentMatchers.anyMap(),
                    anyString(), anyString(), anyString())).thenReturn(true);
            // #16 资损缺口：0.01 元的回调不能结掉 3699 元的订单
            when(portalOrderService.checkOrderAmount("SN202610270001", "0.01")).thenReturn(false);

            String result = alipayService.notify(params("SN202610270001", "0.01"));

            assertThat(result).isEqualTo("failure");
            verify(portalOrderService, never()).paySuccessByOrderSn(anyString(), anyInt());
        }
    }

    @Test
    @DisplayName("notify_验签通过且金额一致_返回success并触发支付成功")
    void notify_验签通过且金额一致_返回success并触发支付成功() {
        try (MockedStatic<AlipaySignature> signature = mockStatic(AlipaySignature.class)) {
            signature.when(() -> AlipaySignature.rsaCheckV1(
                    org.mockito.ArgumentMatchers.anyMap(),
                    anyString(), anyString(), anyString())).thenReturn(true);
            when(portalOrderService.checkOrderAmount("SN202610270001", "3699.00")).thenReturn(true);

            String result = alipayService.notify(params("SN202610270001", "3699.00"));

            assertThat(result).isEqualTo("success");
            // payType=1 表示支付宝；该方法内部已做幂等裁决
            verify(portalOrderService).paySuccessByOrderSn("SN202610270001", 1);
        }
    }

    @Test
    @DisplayName("notify_非TRADE_SUCCESS状态_返回failure不改单")
    void notify_非TRADE_SUCCESS状态_返回failure不改单() {
        try (MockedStatic<AlipaySignature> signature = mockStatic(AlipaySignature.class)) {
            signature.when(() -> AlipaySignature.rsaCheckV1(
                    org.mockito.ArgumentMatchers.anyMap(),
                    anyString(), anyString(), anyString())).thenReturn(true);

            Map<String, String> params = params("SN202610270001", "3699.00");
            params.put("trade_status", "WAIT_BUYER_PAY");

            String result = alipayService.notify(params);

            assertThat(result).isEqualTo("failure");
            verify(portalOrderService, never()).checkOrderAmount(anyString(), anyString());
        }
    }

    @Test
    @DisplayName("pay_成功生成支付表单并带上同步异步回调地址")
    void pay_成功生成支付表单并带上同步异步回调地址() throws Exception {
        alipayConfig.setNotifyUrl("http://localhost:8085/alipay/notify");
        alipayConfig.setReturnUrl("http://localhost:8060/#/paySuccess");
        AlipayTradePagePayResponse response = mock(AlipayTradePagePayResponse.class);
        when(response.getBody()).thenReturn("<form id='alipaysubmit'></form>");
        when(alipayClient.pageExecute(any(AlipayTradePagePayRequest.class))).thenReturn(response);

        AlipayParam param = new AlipayParam();
        param.setOutTradeNo("SN202610270001");
        param.setTotalAmount(new BigDecimal("3699.00"));
        param.setSubject("测试订单");

        String form = alipayService.pay(param);

        ArgumentCaptor<AlipayTradePagePayRequest> captor =
                ArgumentCaptor.forClass(AlipayTradePagePayRequest.class);
        verify(alipayClient).pageExecute(captor.capture());
        assertThat(form).contains("alipaysubmit");
        // 回调地址漏设 → 支付成功后拿不到通知，订单永远停在待付款
        assertThat(captor.getValue().getNotifyUrl()).isEqualTo("http://localhost:8085/alipay/notify");
        assertThat(captor.getValue().getBizContent()).contains("SN202610270001").contains("3699");
    }

    @Test
    @DisplayName("pay_SDK抛异常_吞掉异常返回null不冒泡")
    void pay_SDK抛异常_吞掉异常返回null不冒泡() throws Exception {
        when(alipayClient.pageExecute(any(AlipayTradePagePayRequest.class)))
                .thenThrow(new AlipayApiException("gateway down"));

        AlipayParam param = new AlipayParam();
        param.setOutTradeNo("SN202610270001");
        param.setTotalAmount(new BigDecimal("3699.00"));
        param.setSubject("测试订单");

        // SDK 异常不应该变成 500 抛给前端
        assertThat(alipayService.pay(param)).isNull();
    }

    @Test
    @DisplayName("query_查询到支付成功且金额一致_触发支付成功")
    void query_查询到支付成功且金额一致_触发支付成功() throws Exception {
        AlipayTradeQueryResponse response = mock(AlipayTradeQueryResponse.class);
        when(response.isSuccess()).thenReturn(true);
        when(response.getTradeStatus()).thenReturn("TRADE_SUCCESS");
        when(response.getTotalAmount()).thenReturn("3699.00");
        when(alipayClient.execute(any(AlipayTradeQueryRequest.class))).thenReturn(response);
        when(portalOrderService.checkOrderAmount("SN202610270001", "3699.00")).thenReturn(true);

        String status = alipayService.query("SN202610270001", null);

        assertThat(status).isEqualTo("TRADE_SUCCESS");
        // query 是回调丢失时的兜底，同样必须先过金额校验
        verify(portalOrderService).paySuccessByOrderSn("SN202610270001", 1);
    }

    @Test
    @DisplayName("query_SDK异常返回null_不抛NPE")
    void query_SDK异常返回null_不抛NPE() throws Exception {
        when(alipayClient.execute(any(AlipayTradeQueryRequest.class)))
                .thenThrow(new AlipayApiException("timeout"));

        // 修复点：原实现直接 response.isSuccess() → NPE，恰好在最需要兜底的时候崩
        assertThat(alipayService.query("SN202610270001", null)).isNull();
        verify(portalOrderService, never()).paySuccessByOrderSn(anyString(), anyInt());
    }

    private Map<String, String> params(String outTradeNo, String totalAmount) {
        Map<String, String> params = new HashMap<>();
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("out_trade_no", outTradeNo);
        if (totalAmount != null) {
            params.put("total_amount", totalAmount);
        }
        params.put("sign", "mock-sign");
        return params;
    }
}
