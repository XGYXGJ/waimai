package com.waimai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.waimai.common.exception.BizException;
import com.waimai.common.result.ResultCode;
import com.waimai.common.util.GeoUtil;
import com.waimai.common.util.JsonUtil;
import com.waimai.entity.Merchant;
import com.waimai.entity.Orders;
import com.waimai.entity.Rider;
import com.waimai.mapper.MerchantMapper;
import com.waimai.mapper.OrdersMapper;
import com.waimai.mapper.RiderMapper;
import com.waimai.websocket.WaimaiWsHandler;
import com.waimai.websocket.WsPusher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 骑手端（L1~L3 弹性）：抢单大厅、我的配送、骑手身份解析、位置上报。
 * 骑手端是可裁剪模块：L1 只做抢单+状态确认，L2 加地图，L3 加实时定位。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiderService {

    private final RiderMapper riderMapper;
    private final OrdersMapper ordersMapper;
    private final MerchantMapper merchantMapper;
    private final TrackService trackService;
    private final OrderService orderService;
    private final WsPusher wsPusher;

    /** rider.work_status：0 休息 / 1 空闲 / 2 配送中 */
    public static final int WORK_REST = 0;
    public static final int WORK_IDLE = 1;
    public static final int WORK_BUSY = 2;

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

    /**
     * 抢单大厅：待接订单（已接单/待取餐状态且无骑手）。
     *
     * <p>排序：优先按「骑手当前位置 → 商家」的直线距离由近到远；骑手从未上报过位置时
     * 退回按下单时间倒序（距离为 null 的排在有距离的之后，不会因为没定位就看不到单）。
     */
    public List<Map<String, Object>> grabHall(Long userId) {
        List<Orders> list = ordersMapper.selectList(new QueryWrapper<Orders>()
                .isNull("rider_id")
                .in("status", "ACCEPTED", "WAITING_PICKUP")
                .orderByDesc("created_at").last("limit 50"));
        return brief(list, riderPoint(userId));
    }

    /** 我的配送订单（进行中 + 已送达） */
    public List<Map<String, Object>> myDeliveries(Long riderId) {
        return deliveries(riderId, List.of("WAITING_PICKUP", "DELIVERING", "DELIVERED"));
    }

    /**
     * 历史订单：只含已送达。
     *
     * <p>原先「进行中」和「历史」都调 myDeliveries（状态集合含 DELIVERED），
     * 两个 tab 渲染出完全相同的内容 —— 在途单出现在历史里、已送达单出现在进行中里。
     */
    public List<Map<String, Object>> myHistory(Long riderId) {
        return deliveries(riderId, List.of("DELIVERED"));
    }

    private List<Map<String, Object>> deliveries(Long riderId, List<String> statuses) {
        List<Orders> list = ordersMapper.selectList(new QueryWrapper<Orders>()
                .eq("rider_id", riderId)
                .in("status", statuses)
                .orderByDesc("updated_at").last("limit 50"));
        return brief(list, null);
    }

    /**
     * 骑手位置上报（HTTP 通道，与 WS 共用 TrackService）。
     *
     * <p>orderId 可空（大厅里还没接单时也要能上报自己的位置）。带了 orderId 就必须校验
     * 归属 —— 否则任何骑手都能把坐标写到别人订单的追踪视图里。
     */
    public void reportLocation(Long userId, double lng, double lat, Long orderId) {
        if (orderId != null && !orderService.canTrack(userId, orderId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权上报该订单位置");
        }
        trackService.report(userId, lng, lat, orderId);
    }

    /**
     * 接单：登记商家为位置观察者，立刻推一次当前位置。
     *
     * <p>{@code work_status} 由 {@link #refreshWorkStatus} 按「手上有几个在途单」重算，
     * 而不是在这里硬编码 —— 硬编码会在「同时接两单、送完其中一单」时误判为空闲。
     */
    public void grab(Long userId, Long orderId) {
        Long riderId = riderIdOf(userId);
        orderService.grabOrder(orderId, riderId);
        refreshWorkStatus(riderId);
        // 商家从此也能看到骑手位置（接单即登记，之后每次广播都会推给它）
        Orders order = ordersMapper.selectById(orderId);
        if (order != null && order.getMerchantId() != null) {
            Merchant merchant = merchantMapper.selectById(order.getMerchantId());
            if (merchant != null && merchant.getUserId() != null) {
                wsPusher.watchRiderLocation(orderId, merchant.getUserId());
            }
        }
        announceLocation(userId, orderId);
    }

    /** 取餐：仍是配送中；归档一次轨迹并立即广播位置 */
    public void pickup(Long userId, Long orderId) {
        Long riderId = riderIdOf(userId);
        orderService.pickup(orderId, riderId);
        refreshWorkStatus(riderId);
        trackService.archive(riderId, orderId);
        announceLocation(userId, orderId);
    }

    /** 送达：归档完整轨迹并立即广播最终位置；同时释放该订单的位置观察登记 */
    public void deliver(Long userId, Long orderId) {
        Long riderId = riderIdOf(userId);
        orderService.deliver(orderId, riderId);
        refreshWorkStatus(riderId);
        trackService.archive(riderId, orderId);
        wsPusher.unwatchOrder(orderId);
        announceLocation(userId, orderId);
    }

    /**
     * 按「在途订单数」重算 work_status：0 休息 / 1 空闲 / 2 配送中。
     *
     * <p>不能只按最后一次动作硬编码：骑手可能同时接了多单，送完一单并不代表他空闲了。
     * 管理端「骑手上线 / 空闲 / 配送中」三个数都依赖这个字段准确。
     */
    private void refreshWorkStatus(Long riderId) {
        Long inFlight = ordersMapper.selectCount(new QueryWrapper<Orders>()
                .eq("rider_id", riderId)
                .in("status", "WAITING_PICKUP", "DELIVERING"));
        int target = inFlight != null && inFlight > 0 ? WORK_BUSY : WORK_IDLE;
        Rider r = riderMapper.selectById(riderId);
        if (r == null || Objects.equals(r.getWorkStatus(), target)) return;
        r.setWorkStatus(target);
        riderMapper.updateById(r);
    }

    /**
     * 立刻把骑手当前位置推给订阅该订单的用户 + 该订单的商家。
     *
     * <p>取餐 / 送达这两个节点要求「状态一变，位置立刻更新」，所以这里主动推一次；
     * 骑行过程中的 5 分钟节流广播由 {@code ScheduleJobs.broadcastRiderLocations()} 负责。
     */
    private void announceLocation(Long userId, Long orderId) {
        Map<String, Object> loc = trackService.latest(riderIdOf(userId));
        if (loc == null) return;
        Object lng = loc.get("lng");
        Object lat = loc.get("lat");
        if (lng == null || lat == null) return;
        try {
            wsPusher.pushRiderLocation(orderId,
                    Double.parseDouble(String.valueOf(lng)),
                    Double.parseDouble(String.valueOf(lat)));
        } catch (NumberFormatException e) {
            log.warn("[骑手端] 位置广播跳过，无法解析：{}", loc);
        }
    }

    /**
     * 骑手身份信息（附带派生统计：在线数、今日单数）。
     *
     * <p>{@code online} 用 WS 在线 + 在途订单数派生，而不是直接读 work_status：
     * 自助注册的骑手 work_status 停在默认值 0（「休息」），若把「上线」等同于
     * {@code work_status >= 1}，这类骑手会永远不计入，管理端那个数又变成死的。
     */
    public Map<String, Object> riderProfile(Long userId) {
        Long riderId = riderIdOf(userId);
        Rider r = riderOf(userId);
        Long inFlight = ordersMapper.selectCount(new QueryWrapper<Orders>()
                .eq("rider_id", riderId)
                .in("status", "WAITING_PICKUP", "DELIVERING"));
        Long today = ordersMapper.selectCount(new QueryWrapper<Orders>()
                .eq("rider_id", riderId)
                .ge("created_at", LocalDate.now().atStartOfDay()));
        boolean wsOnline = WaimaiWsHandler.USER_SESSIONS.containsKey(userId);

        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", r.getId());
        vo.put("userId", r.getUserId());
        vo.put("realName", r.getRealName());
        vo.put("phone", r.getPhone());
        vo.put("vehicle", r.getVehicle());
        vo.put("auditStatus", r.getAuditStatus());
        vo.put("workStatus", r.getWorkStatus());
        vo.put("todayOrders", today == null ? 0 : today.intValue());
        vo.put("inFlightOrders", inFlight == null ? 0 : inFlight.intValue());
        vo.put("wsOnline", wsOnline);
        vo.put("online", wsOnline || (inFlight != null && inFlight > 0));
        vo.put("createdAt", r.getCreatedAt());
        return vo;
    }

    /** 上下班切换：休息(0) ⇄ 空闲(1)。在途有单时不允许切到休息 */
    public void setOnline(Long userId, boolean online) {
        Long riderId = riderIdOf(userId);
        Rider r = riderMapper.selectById(riderId);
        if (r == null) throw new BizException(ResultCode.FORBIDDEN, "当前账号不是骑手");
        if (!online) {
            Long inFlight = ordersMapper.selectCount(new QueryWrapper<Orders>()
                    .eq("rider_id", riderId)
                    .in("status", "WAITING_PICKUP", "DELIVERING"));
            if (inFlight != null && inFlight > 0) {
                throw new BizException("还有 " + inFlight + " 单在配送中，请先完成再下线");
            }
            r.setWorkStatus(WORK_REST);
        } else {
            refreshWorkStatus(riderId);
        }
        riderMapper.updateById(r);
    }

    /**
     * 组装骑手端订单卡片。
     *
     * @param origin 骑手最近一次上报的位置 {@code [lng, lat]}；null 表示没有定位
     */
    private List<Map<String, Object>> brief(List<Orders> list, double[] origin) {
        if (list.isEmpty()) return List.of();

        // 商家坐标批量取，避免逐单 selectById 的 N+1
        Map<Long, Merchant> merchants = list.stream()
                .map(Orders::getMerchantId)
                .filter(Objects::nonNull)
                .distinct()
                .map(merchantMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Merchant::getId, Function.identity(), (a, b) -> a));

        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Orders o : list) {
            out.add(briefOne(o, merchants.get(o.getMerchantId()), origin));
        }
        Comparator<Map<String, Object>> byDistance = Comparator
                .comparing((Map<String, Object> m) -> m.get("pickupDistanceKm") == null ? 1 : 0)
                .thenComparing(m -> (BigDecimal) m.get("pickupDistanceKm"),
                        Comparator.nullsLast(Comparator.naturalOrder()));
        out.sort(byDistance);
        return out;
    }

    private Map<String, Object> briefOne(Orders o, Merchant m, double[] origin) {
        Map<String, Object> addr = parseAddress(o.getAddressSnapshot());
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("orderId", o.getId());
        vo.put("orderNo", o.getOrderNo());
        vo.put("status", o.getStatus());

        vo.put("merchantId", o.getMerchantId());
        vo.put("merchantName", m == null ? "" : nullToEmpty(m.getShopName()));
        vo.put("merchantAddress", m == null ? "" : nullToEmpty(m.getAddress()));

        // 地址快照是下单时写下的 JSON 文本，拆成结构化字段给前端直接展示（不要把原文丢给前端）
        vo.put("contact", str(addr.get("contact")));
        vo.put("phone", str(addr.get("phone")));
        vo.put("addressText", str(addr.get("detail")));
        vo.put("lng", addr.get("lng"));
        vo.put("lat", addr.get("lat"));

        vo.put("payAmount", o.getPayAmount());
        vo.put("deliveryFee", o.getDeliveryFee());   // 该单配送费（用户实付的那部分）
        vo.put("riderIncome", o.getRiderIncome());   // 该单骑手实收 = 配送费 × (1 − 骑手抽成)
        vo.put("distanceKm", o.getDistanceKm());     // 商家 → 收货地址（下单时快照）
        vo.put("pickupDistanceKm", pickupDistance(origin, m)); // 骑手当前位置 → 商家（实时算）
        vo.put("pickupTime", o.getPickupTime());     // 历史页要显示送达/取餐时间
        vo.put("deliveredTime", o.getDeliveredTime());
        vo.put("createdAt", o.getCreatedAt());
        return vo;
    }

    /** 骑手当前位置 → 商家 的直线距离（km，2 位小数）；任一坐标缺失返回 null */
    private BigDecimal pickupDistance(double[] origin, Merchant m) {
        if (origin == null || m == null || m.getLng() == null || m.getLat() == null) return null;
        double km = GeoUtil.distanceKm(origin[0], origin[1],
                m.getLng().doubleValue(), m.getLat().doubleValue());
        return BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP);
    }

    /** 骑手最近一次上报的位置 {@code [lng, lat]}；从未上报过返回 null */
    private double[] riderPoint(Long userId) {
        Map<String, Object> loc = trackService.latest(riderIdOf(userId));
        if (loc == null) return null;
        Object lng = loc.get("lng");
        Object lat = loc.get("lat");
        if (lng == null || lat == null) return null;
        try {
            return new double[]{ Double.parseDouble(String.valueOf(lng)),
                                 Double.parseDouble(String.valueOf(lat)) };
        } catch (NumberFormatException e) {
            log.warn("[骑手端] 骑手位置解析失败：{}", loc);
            return null;
        }
    }

    /**
     * 解析 address_snapshot（下单时写入的 JSON 文本）。
     * 解析失败时降级为「把原文当地址展示」，不抛异常 —— 一条脏数据不该让整个大厅 500。
     */
    private Map<String, Object> parseAddress(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) return Map.of();
        try {
            Map<String, Object> parsed = JsonUtil.fromJson(snapshot, Map.class);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            log.warn("[骑手端] address_snapshot 解析失败，降级为原文展示：{}", snapshot);
            return Map.of("detail", snapshot);
        }
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
