package com.waimai.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 订单超时自动取消：TTL + 死信队列
 */
@Configuration
@EnableRabbit
public class RabbitMQConfig {

    public static final String DELAY_EXCHANGE = "waimai.order.delay-ex";
    public static final String DELAY_QUEUE = "waimai.order.delay-q";
    public static final String DLX_EXCHANGE = "waimai.order.dlx";
    public static final String TIMEOUT_QUEUE = "waimai.order.timeout-q";
    public static final String TIMEOUT_ROUTING_KEY = "order.timeout";
    public static final String DELAY_ROUTING_KEY = "order.delay";

    /**
     * 未支付订单自动取消时长（分钟）。
     * 延迟队列 TTL 与前端支付页倒计时共用这一个常量，避免两边各写一份而对不上。
     */
    public static final int ORDER_PAY_TIMEOUT_MINUTES = 15;

    @Bean
    public DirectExchange delayExchange() {
        return new DirectExchange(DELAY_EXCHANGE);
    }

    @Bean
    public Queue delayQueue() {
        return QueueBuilder.durable(DELAY_QUEUE)
                .ttl(ORDER_PAY_TIMEOUT_MINUTES * 60 * 1000)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(TIMEOUT_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    @Bean
    public Queue timeoutQueue() {
        return QueueBuilder.durable(TIMEOUT_QUEUE).build();
    }

    @Bean
    public Binding delayBinding() {
        return BindingBuilder.bind(delayQueue()).to(delayExchange()).with(DELAY_ROUTING_KEY);
    }

    @Bean
    public Binding timeoutBinding() {
        return BindingBuilder.bind(timeoutQueue()).to(dlxExchange()).with(TIMEOUT_ROUTING_KEY);
    }
}
