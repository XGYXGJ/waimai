package com.waimai.mq;

import com.waimai.config.RabbitMQConfig;
import com.waimai.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 订单超时监听：延迟队列 TTL 到期 → 死信队列 → 自动取消未支付订单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutListener {

    private final OrderService orderService;

    @RabbitListener(queues = RabbitMQConfig.TIMEOUT_QUEUE)
    public void onTimeout(String orderIdStr) {
        try {
            long orderId = Long.parseLong(orderIdStr);
            log.info("order {} timeout event received", orderId);
            orderService.cancelOnTimeout(orderId);
        } catch (Exception e) {
            log.error("handle order timeout error: {}", e.getMessage());
        }
    }
}
