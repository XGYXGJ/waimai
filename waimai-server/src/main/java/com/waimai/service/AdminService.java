package com.waimai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.waimai.common.exception.BizException;
import com.waimai.entity.*;
import com.waimai.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 管理端：用户/商户/骑手审核、全量订单、退款、评价治理、竞价审核、数据大屏。
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserMapper userMapper;
    private final MerchantMapper merchantMapper;
    private final RiderMapper riderMapper;
    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final ReviewMapper reviewMapper;
    private final BidCampaignMapper bidCampaignMapper;
    private final OrderService orderService;

    /* ---------- 商户 ---------- */

    public Map<String, Object> merchants(Integer auditStatus, int page, int size) {
        QueryWrapper<Merchant> qw = new QueryWrapper<Merchant>()
                .eq(auditStatus != null, "audit_status", auditStatus)
                .orderByDesc("created_at");
        long total = merchantMapper.selectCount(qw);
        List<Merchant> list = merchantMapper.selectList(qw.last("limit " + ((page - 1) * size) + "," + size));
        return Map.of("total", total, "records", list);
    }

    public void auditMerchant(Long id, boolean pass, String remark) {
        Merchant m = merchantMapper.selectById(id);
        if (m == null) throw new BizException("商户不存在");
        m.setAuditStatus(pass ? 1 : 2);
        m.setAuditRemark(remark);
        merchantMapper.updateById(m);
    }

    /* ---------- 骑手 ---------- */

    public Map<String, Object> riders(Integer auditStatus, int page, int size) {
        QueryWrapper<Rider> qw = new QueryWrapper<Rider>()
                .eq(auditStatus != null, "audit_status", auditStatus)
                .orderByDesc("created_at");
        long total = riderMapper.selectCount(qw);
        List<Rider> list = riderMapper.selectList(qw.last("limit " + ((page - 1) * size) + "," + size));
        return Map.of("total", total, "records", list);
    }

    public void auditRider(Long id, boolean pass) {
        Rider r = riderMapper.selectById(id);
        if (r == null) throw new BizException("骑手不存在");
        r.setAuditStatus(pass ? 1 : 2);
        riderMapper.updateById(r);
    }

    /* ---------- 用户 ---------- */

    public Map<String, Object> users(String role, int page, int size) {
        QueryWrapper<User> qw = new QueryWrapper<User>()
                .eq(role != null && !role.isBlank(), "role", role)
                .orderByDesc("created_at");
        long total = userMapper.selectCount(qw);
        List<User> list = userMapper.selectList(qw.last("limit " + ((page - 1) * size) + "," + size));
        list.forEach(u -> u.setPasswordHash(null));
        return Map.of("total", total, "records", list);
    }

    public void changeUserStatus(Long id, Integer status) {
        User u = userMapper.selectById(id);
        if (u == null) throw new BizException("用户不存在");
        u.setStatus(status);
        userMapper.updateById(u);
    }

    /* ---------- 订单 ---------- */

    public Map<String, Object> orders(String keyword, String status, int page, int size) {
        QueryWrapper<Orders> qw = new QueryWrapper<Orders>()
                .eq(status != null && !status.isBlank(), "status", status)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like("order_no", keyword).or().like("address_snapshot", keyword))
                .orderByDesc("created_at");
        long total = ordersMapper.selectCount(qw);
        List<Orders> list = ordersMapper.selectList(qw.last("limit " + ((page - 1) * size) + "," + size));
        return Map.of("total", total, "records", list);
    }

    public void refund(Long orderId) {
        Orders o = ordersMapper.selectById(orderId);
        if (o == null) throw new BizException("订单不存在");
        if (List.of("REFUNDED", "CANCELLED", "DELIVERED").contains(o.getStatus()))
            throw new BizException("当前状态不可退款");
        // 回补库存
        for (OrderItem oi : orderItemMapper.selectList(new QueryWrapper<OrderItem>().eq("order_id", orderId))) {
            orderService.restoreStock(oi.getDishId(), oi.getQuantity());
        }
        o.setStatus("REFUNDED");
        o.setCancelBy("ADMIN");
        o.setCancelReason("平台退款");
        ordersMapper.updateById(o);
    }

    /* ---------- 评价治理 ---------- */

    public Map<String, Object> reviews(Long merchantId, int page, int size) {
        QueryWrapper<Review> qw = new QueryWrapper<Review>()
                .eq(merchantId != null, "merchant_id", merchantId)
                .orderByDesc("created_at");
        long total = reviewMapper.selectCount(qw);
        List<Review> list = reviewMapper.selectList(qw.last("limit " + ((page - 1) * size) + "," + size));
        return Map.of("total", total, "records", list);
    }

    public void deleteReview(Long id) {
        reviewMapper.deleteById(id);
    }

    /* ---------- 竞价审核 ---------- */

    public void auditCampaign(Long id, boolean pass) {
        BidCampaign c = bidCampaignMapper.selectById(id);
        if (c == null) throw new BizException("投放计划不存在");
        c.setStatus(pass ? 1 : 0);
        bidCampaignMapper.updateById(c);
    }

    /* ---------- 数据大屏 ---------- */

    public Map<String, Object> dashboard() {
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime weekStart = today.minusDays(7).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

        // GMV（按 payAmount 统计已支付/已完成订单）
        Map<String, Object> gmv = new LinkedHashMap<>();
        gmv.put("today", sumPayAmount(dayStart, LocalDateTime.now()));
        gmv.put("week", sumPayAmount(weekStart, LocalDateTime.now()));
        gmv.put("month", sumPayAmount(monthStart, LocalDateTime.now()));

        // 订单量趋势（近7天）
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            Long cnt = ordersMapper.selectCount(new QueryWrapper<Orders>()
                    .ge("created_at", d.atStartOfDay())
                    .lt("created_at", d.plusDays(1).atStartOfDay()));
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("date", d.toString());
            p.put("count", cnt);
            trend.add(p);
        }

        Long newUsers = userMapper.selectCount(new QueryWrapper<User>()
                .ge("created_at", weekStart));
        Long merchantCount = merchantMapper.selectCount(new QueryWrapper<Merchant>().eq("audit_status", 1));
        Long riderOnline = riderMapper.selectCount(new QueryWrapper<Rider>().eq("work_status", 1));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gmv", gmv);
        result.put("orderTrend", trend);
        result.put("newUsers", newUsers);
        result.put("merchantCount", merchantCount);
        result.put("riderOnline", riderOnline);
        result.put("userCount", userMapper.selectCount(null));
        result.put("orderCount", ordersMapper.selectCount(null));
        return result;
    }

    private BigDecimal sumPayAmount(LocalDateTime from, LocalDateTime to) {
        List<Orders> list = ordersMapper.selectList(new QueryWrapper<Orders>()
                .ge("created_at", from).lt("created_at", to)
                .in("status", "PAID", "ACCEPTED", "WAITING_PICKUP", "DELIVERING", "DELIVERED"));
        return list.stream().map(Orders::getPayAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
