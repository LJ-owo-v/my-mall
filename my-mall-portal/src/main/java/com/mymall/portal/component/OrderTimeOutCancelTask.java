package com.mymall.portal.component;

import com.mymall.portal.service.OmsPortalOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时未支付订单自动取消（兜底定时任务）
 *
 * 为什么需要它（两条路径互补）：
 *   主路径：下单时发 MQ 延迟消息 → 到期后 CancelOrderReceiver 消费 → cancelOrder
 *   兜底：本任务周期性扫描超时订单 → cancelTimeOutOrder（不依赖 MQ）
 *
 * 关于扫描频率（10 分钟）：
 *   主路径与兜底用的是【同一个阈值】—— sendDelayMessageCancelOrder 读的是
 *   orderSetting.getNormalOrderOvertime()（120 分钟），getTimeOutOrders 也读同一个字段。
 *   因此 MQ 按期投递时订单在 120 分钟被取消；即便 MQ 消息未按期到达（或队头阻塞延迟），
 *   兜底最多再等 10 分钟（一个扫描周期）→ 最坏延迟 120 + 10 分钟，与主路径无实质差别。
 *   ★ 注：早期曾评估"提高扫描频率以补偿延迟队列队头阻塞"，但经核实：
 *     · 队头阻塞的前提是【队列中存在不同 TTL 的消息】，而 sendDelayMessageCancelOrder
 *       只读 normalOrderOvertime，**所有订单消息 TTL 相同** → 队列内 FIFO 有序，
 *       队头必然最先到期 → 当前不存在队头阻塞；
 *     · 且 oms_order 表只有 PRIMARY 索引，(status, create_time) 无索引，
 *       提高频率会使全表扫描次数成倍增加（订单表是增长最快的表）。
 *     → 故保持 10 分钟不变（详见 docs/后期优化文档.md #11）。
 */
@Component
public class OrderTimeOutCancelTask {
    private Logger LOGGER = LoggerFactory.getLogger(OrderTimeOutCancelTask.class);
    @Autowired
    private OmsPortalOrderService portalOrderService;

    /** 每 10 分钟扫描一次 */
    @Scheduled(cron = "0 0/10 * ? * ?")
    public void cancelTimeOutOrder(){
        Integer count = portalOrderService.cancelTimeOutOrder();
        LOGGER.info("取消订单，并根据sku编号释放锁定库存，取消订单数量：{}",count);
    }
}
