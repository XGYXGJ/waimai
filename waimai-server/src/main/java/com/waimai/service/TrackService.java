package com.waimai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.waimai.common.util.JsonUtil;
import com.waimai.entity.Orders;
import com.waimai.entity.Rider;
import com.waimai.entity.RiderLocation;
import com.waimai.mapper.OrdersMapper;
import com.waimai.mapper.RiderLocationMapper;
import com.waimai.mapper.RiderMapper;
import com.waimai.websocket.WsPusher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 骑手实时轨迹（L3）：
 * 真实 GPS 与模拟骑行共用同一条上报链路 → Redis 存储 → 订阅推送。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackService {

    private final StringRedisTemplate redis;
    private final RiderMapper riderMapper;
    private final RiderLocationMapper riderLocationMapper;
    private final OrdersMapper ordersMapper;
    private final WsPusher wsPusher;

    /** 骑手位置上报（WS LOCATION_REPORT 或 HTTP POST /api/rider/location） */
    public void report(Long userId, double lng, double lat, Long orderId) {
        Rider rider = riderMapper.selectOne(new QueryWrapper<Rider>().eq("user_id", userId));
        if (rider == null) return;
        long ts = System.currentTimeMillis();
        // 最新位置
        redis.opsForValue().set("rider:loc:" + rider.getId(), JsonUtil.toJson(
                Map.of("lng", lng, "lat", lat, "ts", ts)));
        // 轨迹历史（保留最近500点）
        String key = "rider:track:" + rider.getId();
        redis.opsForZSet().add(key, lng + "," + lat, ts);
        Long size = redis.opsForZSet().zCard(key);
        if (size != null && size > 500) {
            redis.opsForZSet().removeRange(key, 0, size - 500 - 1);
        }
        // 推送给订阅该订单的用户
        if (orderId != null) {
            wsPusher.pushRiderLocation(orderId, lng, lat);
        }
    }

    /**
     * 骑手最近一次上报的位置；没有上报过返回 null。
     *
     * <p>Redis 里的值被外部写坏时返回 null 而不是抛异常 —— 一条脏数据不该让
     * 抢单大厅整页 500（调用方都做了 null 判断）。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> latest(Long riderId) {
        if (riderId == null) return null;
        String json = redis.opsForValue().get("rider:loc:" + riderId);
        if (json == null || json.isBlank()) return null;
        try {
            Map<String, Object> m = JsonUtil.fromJson(json, Map.class);
            return m;
        } catch (Exception e) {
            log.warn("[轨迹] rider:loc:{} 内容无法解析，已忽略：{}", riderId, json);
            return null;
        }
    }

    /**
     * 把 Redis 里的轨迹增量归档到 MySQL（冷热分离）。
     *
     * <p>增量：只写「上次归档之后」新增的点，所以可以重复调用（取餐 / 送达 / 定时任务都会调），
     * 不会把同一条轨迹反复插进表里。已归档的最后时间取自 {@code rider_location} 的最大 created_at，
     * 因此写入的 created_at 必须来自 Redis ZSet 的 score（上报时刻），不能用归档时刻。
     *
     * <p>轨迹 key 是<b>骑手级</b>（{@code rider:track:{riderId}}），不是订单级 ——
     * 同一骑手先后送两单时，第二单的归档会把第一单的旧点也捞出来。所以这里必须按
     * 「本单开始时间」裁剪，否则订单 B 的轨迹会从订单 A 的取货点开始。
     *
     * <p>{@code @Transactional}：并发调用（定时任务 + 送达）读到同一个 since 会双写。
     */
    @Transactional
    public int archive(Long riderId, Long orderId) {
        if (riderId == null || orderId == null) return 0;
        String key = "rider:track:" + riderId;
        var set = redis.opsForZSet().rangeWithScores(key, 0, -1);
        if (set == null || set.isEmpty()) return 0;

        // 本单起点：早于它的点属于上一单，不能算进本单
        Orders order = ordersMapper.selectById(orderId);
        long since = Math.max(lastArchivedAt(riderId, orderId), orderStartMs(order));

        List<RiderLocation> batch = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> t : set) {
            if (t.getValue() == null || t.getScore() == null) continue;
            if (t.getScore() <= since) continue;   // 已归档或属于上一单
            String[] ll = t.getValue().split(",");
            if (ll.length < 2) continue;
            try {
                RiderLocation loc = new RiderLocation();
                loc.setRiderId(riderId);
                loc.setOrderId(orderId);
                loc.setLng(new java.math.BigDecimal(ll[0]));
                loc.setLat(new java.math.BigDecimal(ll[1]));
                loc.setCreatedAt(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(t.getScore().longValue()), ZoneId.systemDefault()));
                batch.add(loc);
            } catch (RuntimeException ignored) {
                // 脏点（数字越界 / 时间戳异常）直接跳过，不让一条坏数据毁掉整次归档
            }
            if (batch.size() >= 500) break;
        }
        batch.forEach(riderLocationMapper::insert);
        return batch.size();
    }

    /** 本单开始配送的时间（取最早的状态时间）；取不到就退化成 0，不拦 */
    private long orderStartMs(Orders order) {
        if (order == null) return 0L;
        LocalDateTime t = order.getPickupTime() != null ? order.getPickupTime()
                : order.getAcceptTime() != null ? order.getAcceptTime()
                : order.getCreatedAt();
        if (t == null) return 0L;
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** 该骑手该订单已归档到的最后时间（epochMilli）；没有则 0 */
    private long lastArchivedAt(Long riderId, Long orderId) {
        RiderLocation last = riderLocationMapper.selectOne(new QueryWrapper<RiderLocation>()
                .eq("rider_id", riderId)
                .eq("order_id", orderId)
                .orderByDesc("created_at")
                .last("limit 1"));
        if (last == null || last.getCreatedAt() == null) return 0L;
        return last.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
