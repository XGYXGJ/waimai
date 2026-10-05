package com.waimai.common.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis 的固定窗口限流器，用于登录/注册等敏感入口防撞库与批量注册。
 *
 * <p>降级约定：Redis 不可用时<strong>放行</strong>。限流是保护措施，
 * 不能反过来把主流程（登录）打死——这与项目里购物车、幂等锁的降级口径一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitUtil {

    private final StringRedisTemplate redis;

    /**
     * 计数并判断是否放行。
     *
     * @param key           限流维度键，如 {@code rl:login:127.0.0.1}
     * @param limit         窗口内允许的最大次数
     * @param windowSeconds 窗口长度（秒）
     * @return true = 允许通过；false = 已超限
     */
    public boolean allow(String key, int limit, int windowSeconds) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) return true;
            // 首次计数时设置窗口过期，后续请求不重置，保证是「固定窗口」而不是「滑动续期」
            if (count == 1L) redis.expire(key, Duration.ofSeconds(windowSeconds));
            return count <= limit;
        } catch (Exception e) {
            log.warn("限流器不可用，放行本次请求 key={} err={}", key, e.getMessage());
            return true;
        }
    }

    /** 取客户端 IP：优先反向代理头，其次 socket 地址 */
    public static String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return request.getRemoteAddr();
    }
}
