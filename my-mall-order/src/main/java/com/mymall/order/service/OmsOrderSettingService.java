package com.mymall.order.service;

import com.mymall.order.model.OmsOrderSetting;

public interface OmsOrderSettingService {
    OmsOrderSetting getItem(Long id);

    int update(Long id, OmsOrderSetting orderSetting);
}
