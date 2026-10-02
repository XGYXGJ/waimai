package com.waimai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.waimai.common.exception.BizException;
import com.waimai.common.result.ResultCode;
import com.waimai.dto.WebDTO;
import com.waimai.entity.Coupon;
import com.waimai.entity.UserCoupon;
import com.waimai.mapper.CouponMapper;
import com.waimai.mapper.UserCouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final StringRedisTemplate redis;
    private final com.waimai.mapper.MerchantMapper merchantMapper;

    /* ---------- 用户侧 ---------- */

    /** 领券大厅：全平台在售（商家券 + 平台券），附带商家名与领取状态 */
    public Map<String, Object> hall(Long userId) {
        List<Coupon> list = couponMapper.selectList(new QueryWrapper<Coupon>()
                .eq("status", 1)
                .le("start_time", LocalDateTime.now())
                .ge("end_time", LocalDateTime.now())
                .orderByDesc("created_at"));
        List<Map<String, Object>> records = new ArrayList<>();
        for (Coupon c : list) {
            Map<String, Object> m = toVo(c);
            // 商家名
            if (c.getMerchantId() != null && c.getMerchantId() != 0) {
                var merchant = merchantMapper.selectById(c.getMerchantId());
                m.put("merchantName", merchant == null ? "全平台" : merchant.getShopName());
            } else {
                m.put("merchantName", "全平台");
            }
            // 是否已抢完
            boolean soldOut = c.getTotalCount() != null && c.getTotalCount() > 0
                    && c.getReceivedCount() != null && c.getReceivedCount() >= c.getTotalCount();
            m.put("soldOut", soldOut);
            // 本人已领数量 / 是否达限
            long mine = userCouponMapper.selectCount(new QueryWrapper<UserCoupon>()
                    .eq("user_id", userId).eq("coupon_id", c.getId()));
            m.put("receivedMine", mine);
            m.put("reachLimit", c.getPerUserLimit() != null && mine >= c.getPerUserLimit());
            records.add(m);
        }
        return Map.of("records", records);
    }

    /** 某商家可领的优惠券 */
    public List<Map<String, Object>> available(Long merchantId) {
        List<Coupon> list = couponMapper.selectList(new QueryWrapper<Coupon>()
                .eq("status", 1)
                .in("merchant_id", 0L, merchantId)
                .le("start_time", LocalDateTime.now())
                .ge("end_time", LocalDateTime.now()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Coupon c : list) {
            Map<String, Object> m = toVo(c);
            boolean soldOut = c.getTotalCount() != null && c.getTotalCount() > 0
                    && c.getReceivedCount() != null && c.getReceivedCount() >= c.getTotalCount();
            m.put("soldOut", soldOut);
            result.add(m);
        }
        return result;
    }

    public void receive(Long userId, Long couponId) {
        Coupon c = couponMapper.selectById(couponId);
        if (c == null || c.getStatus() != 1) throw new BizException("优惠券不存在或已下架");
        if (LocalDateTime.now().isBefore(c.getStartTime()) || LocalDateTime.now().isAfter(c.getEndTime())) {
            throw new BizException("不在领取时间内");
        }
        // Redis 原子计数控制总量
        if (c.getTotalCount() > 0) {
            Long cnt = redis.opsForValue().increment("coupon:received:" + couponId);
            if (cnt > c.getTotalCount()) {
                redis.opsForValue().decrement("coupon:received:" + couponId);
                throw new BizException("优惠券已抢完");
            }
        }
        // 限领校验
        Long mine = userCouponMapper.selectCount(new QueryWrapper<UserCoupon>()
                .eq("user_id", userId).eq("coupon_id", couponId));
        if (mine >= c.getPerUserLimit()) throw new BizException("已达限领数量");

        UserCoupon uc = new UserCoupon();
        uc.setCouponId(couponId);
        uc.setUserId(userId);
        userCouponMapper.insert(uc);
        c.setReceivedCount(c.getReceivedCount() + 1);
        couponMapper.updateById(c);
    }

    public Map<String, Object> myCoupons(Long userId, Integer status) {
        List<UserCoupon> ucs = userCouponMapper.selectList(new QueryWrapper<UserCoupon>()
                .eq("user_id", userId).eq(status != null, "status", status)
                .orderByDesc("received_at"));
        List<Map<String, Object>> records = new ArrayList<>();
        for (UserCoupon uc : ucs) {
            Coupon c = couponMapper.selectById(uc.getCouponId());
            if (c == null) continue;
            Map<String, Object> m = toVo(c);
            m.put("userCouponId", uc.getId());
            m.put("ucStatus", uc.getStatus());
            records.add(m);
        }
        return Map.of("records", records);
    }

    /** 指定金额下可用的优惠券（下单预览用），附带计算好的优惠额 */
    public List<Map<String, Object>> usable(Long userId, Long merchantId, BigDecimal dishAmount) {
        List<UserCoupon> ucs = userCouponMapper.selectList(new QueryWrapper<UserCoupon>()
                .eq("user_id", userId).eq("status", 0));
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (UserCoupon uc : ucs) {
            Coupon c = couponMapper.selectById(uc.getCouponId());
            if (c == null || c.getStatus() != 1) continue;
            if (c.getMerchantId() != 0 && !c.getMerchantId().equals(merchantId)) continue;
            if (now.isBefore(c.getStartTime()) || now.isAfter(c.getEndTime())) continue;
            BigDecimal discount = computeDiscount(c, dishAmount);
            if (discount == null) continue; // 不满足门槛
            Map<String, Object> m = toVo(c);
            m.put("userCouponId", uc.getId());
            m.put("discountedAmount", discount);
            m.put("usable", true);
            result.add(m);
        }
        result.sort((a, b) -> ((BigDecimal) b.get("discountedAmount")).compareTo((BigDecimal) a.get("discountedAmount")));
        return result;
    }

    /** 计算优惠额；不满足门槛返回 null */
    public BigDecimal computeDiscount(Coupon c, BigDecimal dishAmount) {
        switch (c.getType()) {
            case 1: // 满减
                if (dishAmount.compareTo(c.getThresholdAmount()) < 0) return null;
                return c.getDiscountAmount();
            case 2: // 折扣
                if (dishAmount.compareTo(c.getThresholdAmount()) < 0) return null;
                return dishAmount.multiply(BigDecimal.ONE.subtract(c.getDiscountRate()))
                        .setScale(2, RoundingMode.HALF_UP);
            case 3: // 无门槛
                BigDecimal d = c.getDiscountAmount();
                return d.compareTo(dishAmount) > 0 ? dishAmount : d;
            default:
                return null;
        }
    }

    /* ---------- 商户侧 ---------- */

    public void create(Long merchantId, WebDTO.CouponReq req) {
        Coupon c = new Coupon();
        c.setMerchantId(merchantId);
        c.setName(req.getName());
        c.setType(req.getType());
        c.setThresholdAmount(BigDecimal.valueOf(req.getThresholdAmount() == null ? 0 : req.getThresholdAmount()));
        c.setDiscountAmount(req.getDiscountAmount() == null ? null : BigDecimal.valueOf(req.getDiscountAmount()));
        c.setDiscountRate(req.getDiscountRate() == null ? null : BigDecimal.valueOf(req.getDiscountRate()));
        c.setTotalCount(req.getTotalCount() == null ? 0 : req.getTotalCount());
        c.setPerUserLimit(req.getPerUserLimit() == null ? 1 : req.getPerUserLimit());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        c.setStartTime(LocalDateTime.parse(req.getStartTime(), fmt));
        c.setEndTime(LocalDateTime.parse(req.getEndTime(), fmt));
        c.setStatus(1);
        couponMapper.insert(c);
    }

    public Map<String, Object> merchantCoupons(Long merchantId) {
        return Map.of("records", couponMapper.selectList(
                new QueryWrapper<Coupon>().eq("merchant_id", merchantId).orderByDesc("created_at")));
    }

    public void offShelf(Long merchantId, Long couponId) {
        Coupon c = couponMapper.selectById(couponId);
        if (c == null || !c.getMerchantId().equals(merchantId)) throw new BizException("优惠券不存在");
        c.setStatus(0);
        couponMapper.updateById(c);
    }

    /* ---------- 订单侧核销/回滚 ---------- */

    public void useCoupon(Long userCouponId, Long orderId) {
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null) throw new BizException(ResultCode.COUPON_INVALID, "优惠券不存在");
        if (uc.getStatus() != 0) throw new BizException(ResultCode.COUPON_INVALID, "优惠券不可用");
        uc.setStatus(1);
        uc.setUsedAt(LocalDateTime.now());
        uc.setOrderId(orderId);
        userCouponMapper.updateById(uc);
    }

    public void revertCoupon(Long userCouponId) {
        if (userCouponId == null) return;
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null || uc.getStatus() != 1) return;
        uc.setStatus(0);
        uc.setUsedAt(null);
        uc.setOrderId(null);
        userCouponMapper.updateById(uc);
    }

    private Map<String, Object> toVo(Coupon c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("couponId", c.getId());
        m.put("merchantId", c.getMerchantId());
        m.put("name", c.getName());
        m.put("type", c.getType());
        m.put("thresholdAmount", c.getThresholdAmount());
        m.put("discountAmount", c.getDiscountAmount());
        m.put("discountRate", c.getDiscountRate());
        m.put("startTime", c.getStartTime());
        m.put("endTime", c.getEndTime());
        return m;
    }
}
