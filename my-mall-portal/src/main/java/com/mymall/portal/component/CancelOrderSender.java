package com.mymall.portal.component;

import com.mymall.portal.domain.QueueEnum;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CancelOrderSender {
    @Autowired
    private AmqpTemplate amqpTemplate;  // RabbitMQ 发送模板

    public void sendMessage(Long orderId, final long delayTimes) {
        amqpTemplate.convertAndSend(
                QueueEnum.QUEUE_TTL_ORDER_CANCEL.getExchange(),   // 发到哪个交换机
                QueueEnum.QUEUE_TTL_ORDER_CANCEL.getRouteKey(),   // 用哪个路由键
                orderId,                                          // 消息体：订单ID
                new MessagePostProcessor() {
                    @Override
                    public Message postProcessMessage(Message message) {
                        // 关键：给每条消息单独设置过期时间（毫秒）
                        message.getMessageProperties().setExpiration(String.valueOf(delayTimes));
                        return message;
                    }
                });
    }
}
