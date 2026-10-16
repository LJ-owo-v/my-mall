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
 * ★ 【#11 修复】扫描频率由 10 分钟调整为 2 分钟：
 *   延迟队列用的是「消息级 TTL + 死信」方案，RabbitMQ **只在队头判断过期** ——
 *   当队列中同时存在不同 TTL 的消息时（normal_order_overtime=120 分钟、
 *   flash_order_overtime=60 分钟），队首的长 TTL 消息会把后面的短 TTL 消息**堵住**，
 *   导致秒杀订单的取消被延迟（库存多占用）。
 *   提高兜底频率可用更勤的扫描补偿这个"准点性劣化"（成本：每 2 分钟一次轻量查询）。
 *   （彻底解决需装 rabbitmq_delayed_message_exchange 插件，属运维侧改动，暂不做。）
 */
@Component
public class OrderTimeOutCancelTask {
    private Logger LOGGER = LoggerFactory.getLogger(OrderTimeOutCancelTask.class);
    @Autowired
    private OmsPortalOrderService portalOrderService;

    /** 每 2 分钟扫描一次（原为每 10 分钟） */
    @Scheduled(cron = "0 0/2 * ? * ?")
    public void cancelTimeOutOrder(){
        Integer count = portalOrderService.cancelTimeOutOrder();
        LOGGER.info("取消订单，并根据sku编号释放锁定库存，取消订单数量：{}",count);
    }
}
