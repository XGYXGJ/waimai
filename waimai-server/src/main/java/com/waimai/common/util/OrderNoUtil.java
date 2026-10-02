package com.waimai.common.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class OrderNoUtil {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redis;

    public OrderNoUtil(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String next() {
        String date = LocalDate.now().format(FMT);
        Long seq = redis.opsForValue().increment("order:seq:" + date);
        return date + String.format("%06d", System.currentTimeMillis() % 1000000)
                + String.format("%04d", seq == null ? 1 : seq);
    }
}
