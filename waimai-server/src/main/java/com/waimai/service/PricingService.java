package com.waimai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 计价与分账服务（平台侧统一口径）。
 *
 * <p>配送费不再由商家自己写死，而是按「商家 → 收货地址」的直线距离动态计算：
 * <pre>
 *   配送费 = 起步费 + max(0, 距离 - 免费距离) × 每公里费用     （受单笔上限约束）
 * </pre>
 * 参数全部来自 sys_config，管理端「系统参数」改完即时生效。
 *
 * <p>分账模型（三方之和 = 用户实付，不产生分文误差）：
 * <pre>
 *   商家营业额 = 菜品金额 + 打包费 - 优惠金额        （优惠视为商家承担）
 *   商家实收   = 商家营业额 × (1 - 商家抽成率)
 *   骑手实收   = 配送费     × (1 - 骑手抽成率)
 *   平台收入   = (商家营业额 + 配送费) - 商家实收 - 骑手实收
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private final SysConfigService sysConfigService;

    /* ---------- 配置键（管理端可见） ---------- */
    public static final String KEY_RADIUS = "delivery.radius_km";
    public static final String KEY_BASE_FEE = "delivery.base_fee";
    public static final String KEY_PER_KM_FEE = "delivery.per_km_fee";
    public static final String KEY_FREE_DISTANCE = "delivery.free_distance_km";
    public static final String KEY_MAX_FEE = "delivery.max_fee";
    public static final String KEY_MERCHANT_RATE = "commission.merchant.rate";
    public static final String KEY_RIDER_RATE = "commission.rider.rate";

    /** 商家可设配送范围的硬上限（同时用作商家列表粗筛的包围盒半径，保证不漏商家） */
    public static final double MAX_RADIUS_KM = 20.0;
    /** 配送范围下限，避免商家设成 0 导致永远不展示 */
    public static final double MIN_RADIUS_KM = 0.5;

    /* ---------- 参数读取 ---------- */

    /** 平台默认配送范围（km） */
    public double defaultRadiusKm() {
        return clamp(sysConfigService.getDouble(KEY_RADIUS, 5.0), MIN_RADIUS_KM, MAX_RADIUS_KM);
    }

    public double baseFee() {
        return Math.max(0, sysConfigService.getDouble(KEY_BASE_FEE, 3.0));
    }

    public double perKmFee() {
        return Math.max(0, sysConfigService.getDouble(KEY_PER_KM_FEE, 1.5));
    }

    public double freeDistanceKm() {
        return Math.max(0, sysConfigService.getDouble(KEY_FREE_DISTANCE, 1.0));
    }

    /** 单笔配送费上限，<=0 表示不限制 */
    public double maxFee() {
        return sysConfigService.getDouble(KEY_MAX_FEE, 0);
    }

    /** 平台对商家的抽成比例 [0,1) */
    public double merchantRate() {
        return clamp(sysConfigService.getDouble(KEY_MERCHANT_RATE, 0.1), 0, 0.99);
    }

    /** 平台对骑手的抽成比例 [0,1) */
    public double riderRate() {
        return clamp(sysConfigService.getDouble(KEY_RIDER_RATE, 0.05), 0, 0.99);
    }

    /* ---------- 配送费 ---------- */

    /** 按距离计算配送费（元，保留两位） */
    public BigDecimal deliveryFee(double distanceKm) {
        double billable = Math.max(0, distanceKm - freeDistanceKm());
        double fee = baseFee() + billable * perKmFee();
        double cap = maxFee();
        if (cap > 0) fee = Math.min(fee, cap);
        return BigDecimal.valueOf(Math.max(0, fee)).setScale(2, RoundingMode.HALF_UP);
    }

    /** 商家生效的配送半径：商家单独设置优先，否则取平台默认 */
    public double radiusOf(BigDecimal merchantRadius) {
        if (merchantRadius == null) return defaultRadiusKm();
        return clamp(merchantRadius.doubleValue(), MIN_RADIUS_KM, MAX_RADIUS_KM);
    }

    /**
     * 是否在配送范围内。
     * distance 为 null（地址/商家缺坐标）时无法判定，返回 true 放行，
     * 否则用户只填文字地址就永远下不了单。
     */
    public boolean inRange(BigDecimal merchantRadius, Double distanceKm) {
        if (distanceKm == null) return true;
        return distanceKm <= radiusOf(merchantRadius) + 1e-6;
    }

    /* ---------- 分账 ---------- */

    /** 下单时算好的分账快照（随订单落库，事后改费率不影响历史账目） */
    public record IncomeSplit(BigDecimal merchantGross,
                              BigDecimal merchantIncome,
                              BigDecimal riderIncome,
                              BigDecimal platformIncome,
                              BigDecimal merchantRate,
                              BigDecimal riderRate) { }

    public IncomeSplit split(BigDecimal dishAmount, BigDecimal packageFee,
                             BigDecimal discountAmount, BigDecimal deliveryFee) {
        BigDecimal goods = nz(dishAmount).add(nz(packageFee)).subtract(nz(discountAmount))
                .max(BigDecimal.ZERO);
        BigDecimal fee = nz(deliveryFee);
        BigDecimal mr = BigDecimal.valueOf(merchantRate());
        BigDecimal rr = BigDecimal.valueOf(riderRate());

        BigDecimal merchantIncome = goods.multiply(BigDecimal.ONE.subtract(mr))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal riderIncome = fee.multiply(BigDecimal.ONE.subtract(rr))
                .setScale(2, RoundingMode.HALF_UP);
        // 平台收入用减法兜底，保证 商家实收 + 骑手实收 + 平台收入 == 商家营业额 + 配送费
        BigDecimal platformIncome = goods.add(fee).subtract(merchantIncome).subtract(riderIncome);

        return new IncomeSplit(goods, merchantIncome, riderIncome, platformIncome, mr, rr);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) return min;
        return Math.max(min, Math.min(max, v));
    }
}
