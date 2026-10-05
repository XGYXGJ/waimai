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
    private final PricingService pricingService;
    /** 管理端关键操作留痕（审核/退款/封禁/删评/竞价审核） */
    private final OperationLogService operationLogService;

    /** 计入收入统计的订单状态：已支付且未被退款/取消 */
    private static final List<String> SETTLED_STATUS =
            List.of("PAID", "ACCEPTED", "WAITING_PICKUP", "DELIVERING", "DELIVERED");

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
        operationLogService.record("AUDIT_MERCHANT", "MERCHANT", id,
                (pass ? "审核通过" : "审核驳回")
                        + (remark == null || remark.isBlank() ? "" : "：" + remark));
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
        operationLogService.record("AUDIT_RIDER", "RIDER", id, pass ? "审核通过" : "审核驳回");
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
        operationLogService.record("CHANGE_USER_STATUS", "USER", id,
                "账号状态改为 " + status + (status != null && status == 0 ? "（封禁）" : "（正常）"));
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
        operationLogService.record("REFUND_ORDER", "ORDER", orderId,
                "平台退款 " + o.getOrderNo() + "，金额 " + o.getPayAmount());
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
        Review r = reviewMapper.selectById(id);
        reviewMapper.deleteById(id);
        operationLogService.record("DELETE_REVIEW", "REVIEW", id,
                r == null ? "删除评价" : "删除违规评价（评分 " + r.getRating() + "）");
    }

    /* ---------- 竞价审核 ---------- */

    public void auditCampaign(Long id, boolean pass) {
        BidCampaign c = bidCampaignMapper.selectById(id);
        if (c == null) throw new BizException("投放计划不存在");
        c.setStatus(pass ? 1 : 0);
        bidCampaignMapper.updateById(c);
        operationLogService.record("AUDIT_CAMPAIGN", "BID_CAMPAIGN", id, pass ? "审核通过" : "审核驳回");
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
        // 收入构成（平台/商家/骑手），大屏与「收入结算」页共用同一套口径
        result.put("income", incomeOverview());
        return result;
    }

    /* ---------- 收入结算 ---------- */

    /**
     * 收入总览：GMV 与三方收入（今日 / 近7天 / 本月 / 累计）。
     * 收入取订单上的快照字段（下单时锁定费率），所以在这里改抽成比例不会篡改历史账目。
     */
    public Map<String, Object> incomeOverview() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("today", incomeIn(today.atStartOfDay(), now));
        resp.put("week", incomeIn(today.minusDays(6).atStartOfDay(), now));
        resp.put("month", incomeIn(today.withDayOfMonth(1).atStartOfDay(), now));
        resp.put("total", incomeIn(null, null));
        // 当前生效的费率与计价规则，管理端页面直接展示，避免「数字对不上」的疑问
        Map<String, Object> rate = new LinkedHashMap<>();
        rate.put("merchantRate", pricingService.merchantRate());
        rate.put("riderRate", pricingService.riderRate());
        resp.put("rate", rate);
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("baseFee", pricingService.baseFee());
        rule.put("perKmFee", pricingService.perKmFee());
        rule.put("freeDistanceKm", pricingService.freeDistanceKm());
        rule.put("maxFee", pricingService.maxFee());
        rule.put("radiusKm", pricingService.defaultRadiusKm());
        resp.put("rule", rule);
        return resp;
    }

    /** 单个时间窗口内的收入汇总 */
    private Map<String, Object> incomeIn(LocalDateTime from, LocalDateTime to) {
        QueryWrapper<Orders> qw = new QueryWrapper<Orders>().in("status", SETTLED_STATUS);
        if (from != null) qw.ge("created_at", from);
        if (to != null) qw.lt("created_at", to);
        List<Orders> list = ordersMapper.selectList(qw);

        BigDecimal gmv = BigDecimal.ZERO, merchant = BigDecimal.ZERO,
                rider = BigDecimal.ZERO, platform = BigDecimal.ZERO;
        for (Orders o : list) {
            gmv = gmv.add(nz(o.getPayAmount()));
            merchant = merchant.add(nz(o.getMerchantIncome()));
            rider = rider.add(nz(o.getRiderIncome()));
            platform = platform.add(nz(o.getPlatformIncome()));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gmv", gmv);
        m.put("platformIncome", platform);
        m.put("merchantIncome", merchant);
        m.put("riderIncome", rider);
        m.put("orderCount", list.size());
        return m;
    }

    /** 按商家汇总收入（管理端「收入结算」页表格），按 GMV 倒序分页 */
    public Map<String, Object> incomeByMerchant(int page, int size) {
        List<Orders> all = ordersMapper.selectList(new QueryWrapper<Orders>()
                .select("merchant_id", "pay_amount", "dish_amount", "package_fee", "discount_amount",
                        "delivery_fee", "merchant_income", "rider_income", "platform_income")
                .in("status", SETTLED_STATUS));

        Map<Long, Map<String, Object>> agg = new LinkedHashMap<>();
        for (Orders o : all) {
            Map<String, Object> row = agg.computeIfAbsent(o.getMerchantId(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("merchantId", k);
                m.put("orderCount", 0);
                m.put("gmv", BigDecimal.ZERO);
                m.put("dishAmount", BigDecimal.ZERO);
                m.put("deliveryFee", BigDecimal.ZERO);
                m.put("merchantIncome", BigDecimal.ZERO);
                m.put("riderIncome", BigDecimal.ZERO);
                m.put("platformIncome", BigDecimal.ZERO);
                return m;
            });
            add(row, "orderCount", 1);
            add(row, "gmv", nz(o.getPayAmount()));
            add(row, "dishAmount", nz(o.getDishAmount()).add(nz(o.getPackageFee())).subtract(nz(o.getDiscountAmount())));
            add(row, "deliveryFee", nz(o.getDeliveryFee()));
            add(row, "merchantIncome", nz(o.getMerchantIncome()));
            add(row, "riderIncome", nz(o.getRiderIncome()));
            add(row, "platformIncome", nz(o.getPlatformIncome()));
        }

        List<Map<String, Object>> rows = new ArrayList<>(agg.values());
        rows.sort((a, b) -> ((BigDecimal) b.get("gmv")).compareTo((BigDecimal) a.get("gmv")));

        // 补商家名
        if (!rows.isEmpty()) {
            List<Long> ids = rows.stream().map(r -> (Long) r.get("merchantId")).toList();
            Map<Long, String> names = new HashMap<>();
            for (Merchant m : merchantMapper.selectBatchIds(ids)) names.put(m.getId(), m.getShopName());
            for (Map<String, Object> r : rows) {
                r.put("shopName", names.getOrDefault((Long) r.get("merchantId"), "商家#" + r.get("merchantId")));
            }
        }

        int total = rows.size();
        int from = Math.min(Math.max(0, (page - 1) * size), total);
        int to = Math.min(from + size, total);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("records", rows.subList(from, to));
        resp.put("total", total);
        resp.put("pages", size <= 0 ? 0 : (total + size - 1) / size);
        return resp;
    }

    private void add(Map<String, Object> row, String key, Object delta) {
        if (delta instanceof Integer i) {
            row.put(key, ((Integer) row.get(key)) + i);
        } else {
            row.put(key, ((BigDecimal) row.get(key)).add((BigDecimal) delta));
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
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
