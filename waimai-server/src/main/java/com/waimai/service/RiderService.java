package com.waimai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.waimai.common.exception.BizException;
import com.waimai.common.result.ResultCode;
import com.waimai.entity.Orders;
import com.waimai.entity.Rider;
import com.waimai.mapper.OrdersMapper;
import com.waimai.mapper.RiderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 骑手端（L1~L3 弹性）：抢单大厅、我的配送、骑手身份解析、位置上报。
 * 骑手端是可裁剪模块：L1 只做抢单+状态确认，L2 加地图，L3 加实时定位。
 */
@Service
@RequiredArgsConstructor
public class RiderService {

    private final RiderMapper riderMapper;
    private final OrdersMapper ordersMapper;
    private final TrackService trackService;
    private final OrderService orderService;

    /** 根据登录 userId 获取 rider 表主键 id */
    public Long riderIdOf(Long userId) {
        Rider r = riderMapper.selectOne(new QueryWrapper<Rider>().eq("user_id", userId));
        if (r == null) throw new BizException(ResultCode.FORBIDDEN, "当前账号不是骑手");
        return r.getId();
    }

    public Rider riderOf(Long userId) {
        Rider r = riderMapper.selectOne(new QueryWrapper<Rider>().eq("user_id", userId));
        if (r == null) throw new BizException(ResultCode.FORBIDDEN, "当前账号不是骑手");
        return r;
    }

    /** 抢单大厅：附近待接订单（已接单/待取餐状态且无骑手） */
    public List<Map<String, Object>> grabHall() {
        List<Orders> list = ordersMapper.selectList(new QueryWrapper<Orders>()
                .isNull("rider_id")
                .in("status", "ACCEPTED", "WAITING_PICKUP")
                .orderByDesc("created_at").last("limit 50"));
        return list.stream().map(this::orderBrief).toList();
    }

    /** 我的配送订单 */
    public List<Map<String, Object>> myDeliveries(Long riderId) {
        List<Orders> list = ordersMapper.selectList(new QueryWrapper<Orders>()
                .eq("rider_id", riderId)
                .in("status", "WAITING_PICKUP", "DELIVERING", "DELIVERED")
                .orderByDesc("created_at").last("limit 50"));
        return list.stream().map(this::orderBrief).toList();
    }

    private Map<String, Object> orderBrief(Orders o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", o.getId());
        m.put("orderNo", o.getOrderNo());
        m.put("status", o.getStatus());
        m.put("address", o.getAddressSnapshot());
        m.put("payAmount", o.getPayAmount());
        m.put("createdAt", o.getCreatedAt());
        return m;
    }

    /** 骑手位置上报（HTTP 通道，与 WS 共用 TrackService） */
    public void reportLocation(Long userId, double lng, double lat, Long orderId) {
        trackService.report(userId, lng, lat, orderId);
    }

    /** 接单 */
    public void grab(Long userId, Long orderId) {
        orderService.grabOrder(orderId, riderIdOf(userId));
    }

    /** 取餐 */
    public void pickup(Long userId, Long orderId) {
        orderService.pickup(orderId, riderIdOf(userId));
    }

    /** 送达 */
    public void deliver(Long userId, Long orderId) {
        orderService.deliver(orderId, riderIdOf(userId));
    }
}
