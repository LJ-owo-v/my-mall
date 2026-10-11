package com.mymall.portal.component;

import com.mymall.portal.service.OmsPortalOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderTimeOutCancelTask {
    private Logger LOGGER = LoggerFactory.getLogger(OrderTimeOutCancelTask.class);
    @Autowired
    private OmsPortalOrderService portalOrderService;

    @Scheduled(cron = "0 0/10 * ? * ?")
    public void cancelTimeOutOrder(){
        Integer count = portalOrderService.cancelTimeOutOrder();
        LOGGER.info("取消订单，并根据sku编号释放锁定库存，取消订单数量：{}",count);
    }
}
