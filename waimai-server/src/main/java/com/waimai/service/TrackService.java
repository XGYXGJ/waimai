package com.waimai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.waimai.common.util.JsonUtil;
import com.waimai.entity.Rider;
import com.waimai.entity.RiderLocation;
import com.waimai.mapper.RiderLocationMapper;
import com.waimai.mapper.RiderMapper;
import com.waimai.websocket.WsPusher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 骑手实时轨迹（L3）：
 * 真实 GPS 与模拟骑行共用同一条上报链路 → Redis 存储 → 订阅推送。
 */
@Service
@RequiredArgsConstructor
public class TrackService {

    private final StringRedisTemplate redis;
    private final RiderMapper riderMapper;
    private final RiderLocationMapper riderLocationMapper;
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

    public Map<String, Object> latest(Long riderId) {
        String json = redis.opsForValue().get("rider:loc:" + riderId);
        if (json == null) return null;
        return JsonUtil.fromJson(json, Map.class);
    }

    /** 订单完成时归档轨迹到 MySQL（冷热分离） */
    public void archive(Long riderId, Long orderId) {
        String key = "rider:track:" + riderId;
        var set = redis.opsForZSet().rangeWithScores(key, 0, -1);
        if (set == null || set.isEmpty()) return;
        List<RiderLocation> batch = new ArrayList<>();
        int i = 0;
        for (ZSetOperations.TypedTuple<String> t : set) {
            if (t.getValue() == null || i++ >= 500) break;
            String[] ll = t.getValue().split(",");
            RiderLocation loc = new RiderLocation();
            loc.setRiderId(riderId);
            loc.setOrderId(orderId);
            loc.setLng(new java.math.BigDecimal(ll[0]));
            loc.setLat(new java.math.BigDecimal(ll[1]));
            loc.setCreatedAt(LocalDateTime.now());
            batch.add(loc);
        }
        batch.forEach(riderLocationMapper::insert);
    }
}
