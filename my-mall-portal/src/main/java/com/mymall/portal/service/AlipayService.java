package com.mymall.portal.service;

import com.mymall.portal.domain.AlipayParam;

import java.util.Map;

public interface AlipayService {
    String pay(AlipayParam aliPayParam);

    String webPay(AlipayParam aliPayParam);

    String notify(Map<String, String> params);

    String query(String outTradeNo, String tradeNo);
}
