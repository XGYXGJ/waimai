package com.waimai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.waimai.common.exception.BizException;
import com.waimai.common.result.ResultCode;
import com.waimai.common.util.GeoUtil;
import com.waimai.dto.WebDTO;
import com.waimai.entity.*;
import com.waimai.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantMapper merchantMapper;
    private final MerchantCategoryMapper categoryMapper;
    private final DishMapper dishMapper;
    private final DishCategoryMapper dishCategoryMapper;
    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserService userService;
    private final BidService bidService;
    private final PricingService pricingService;

    /* ---------- 用户侧 ---------- */

    /** 首页：分类 + 附近商家 */
    public Map<String, Object> home(Double lng, Double lat) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("categories", categoryMapper.selectList(
                new QueryWrapper<MerchantCategory>().orderByAsc("sort")));
        resp.put("merchants", nearbyList(lng, lat, null, "综合", 1, 20));
        return resp;
    }

    /** 商户经营分类列表（供店铺设置选择） */
    public List<MerchantCategory> categoryOptions() {
        return categoryMapper.selectList(new QueryWrapper<MerchantCategory>().orderByAsc("sort"));
    }

    /** 商家列表（含竞价广告位，sortType=keyword 时启用竞价） */
    public Map<String, Object> list(Double lng, Double lat, Integer categoryId, String keyword,
                                    String sortType, int page, int size) {
        if (keyword != null && !keyword.isBlank()) {
            Long uid = UserContextUserId();
            if (uid != null) userService.saveSearch(uid, keyword);
        }
        QueryWrapper<Merchant> qw = new QueryWrapper<Merchant>()
                .eq("audit_status", 1)
                .eq("open_status", 1)
                .eq(categoryId != null, "category_id", categoryId);
        if (keyword != null && !keyword.isBlank()) {
            // 商家名或菜品名匹配
            List<Long> dishMerchantIds = dishMapper.selectList(new QueryWrapper<Dish>()
                            .eq("status", 1).like("name", keyword))
                    .stream().map(Dish::getMerchantId).distinct().toList();
            qw.and(w -> w.like("shop_name", keyword)
                    .or(dishMerchantIds.size() > 0, w2 -> w2.in("id", dishMerchantIds)));
        }
        // 地理范围粗筛：包围盒半径取「平台默认范围」与「商家可设上限」的较大者，
        // 保证自定义了更大配送范围的商家不会被粗筛提前筛掉（精确过滤在下面按各自半径做）
        if (lng != null && lat != null) {
            double boxKm = Math.max(pricingService.defaultRadiusKm(), PricingService.MAX_RADIUS_KM);
            double dLat = boxKm / 111.0;
            double dLng = boxKm / (111.0 * Math.max(0.2, Math.cos(Math.toRadians(lat))));
            qw.between("lng", lng - dLng, lng + dLng)
              .between("lat", lat - dLat, lat + dLat);
        }
        List<Merchant> all = merchantMapper.selectList(qw);

        // 计算距离与排序
        List<Map<String, Object>> natural = new ArrayList<>();
        double maxSales = all.stream().mapToDouble(Merchant::getMonthlySales).max().orElse(1);
        for (Merchant m : all) {
            // 超出配送范围的商家：首页/列表都不展示（服务端统一把关，前端不用再判）
            if (!inDeliveryRange(m, lng, lat)) continue;
            Map<String, Object> vo = toVo(m, lng, lat);
            double quality = 0.6 * GeoUtil.normalize(m.getMonthlySales(), 0, maxSales)
                    + 0.4 * (m.getRating().doubleValue() / 5.0);
            double decay = lng == null || lat == null ? 1.0
                    : GeoUtil.distanceDecay(((Number) vo.get("distanceKm")).doubleValue());
            double score = quality * decay;
            vo.put("score", score);
            natural.add(vo);
        }
        if ("rating".equals(sortType)) natural.sort((a, b) -> Double.compare(((Number) b.get("score")).doubleValue(), ((Number) a.get("score")).doubleValue()));
        else if ("sales".equals(sortType)) natural.sort((a, b) -> Long.compare(((Number) b.get("monthlySales")).longValue(), ((Number) a.get("monthlySales")).longValue()));
        else if ("distance".equals(sortType)) natural.sort(Comparator.comparingDouble(a -> ((Number) a.get("distanceKm")).doubleValue()));
        else natural.sort((a, b) -> Double.compare(((Number) b.get("score")).doubleValue(), ((Number) a.get("score")).doubleValue()));

        // 竞价广告位：仅关键词搜索
        List<Map<String, Object>> ads = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            for (Map<String, Object> ad : bidService.adsForKeyword(keyword, lng, lat, UserContextUserId())) {
                Long mid = ((Number) ad.get("merchantId")).longValue();
                Merchant am = merchantMapper.selectById(mid);
                if (am == null) continue;
                // 广告位同样受配送范围约束：超出范围强行展示会导致点进去也下不了单
                if (!inDeliveryRange(am, lng, lat)) continue;
                Map<String, Object> vo = toVo(am, lng, lat);
                vo.put("isAd", true);
                vo.put("adCampaignId", ad.get("campaignId"));
                vo.put("bid", ad.get("bid"));
                ads.add(vo);
            }
        }

        // 合并：广告插入第1/4/7位（0-based 0/3/6）
        List<Map<String, Object>> merged = new ArrayList<>();
        Set<Long> adMerchantIds = new HashSet<>();
        for (Map<String, Object> ad : ads) adMerchantIds.add(((Number) ad.get("id")).longValue());
        natural.removeIf(n -> adMerchantIds.contains(((Number) n.get("id")).longValue()));

        int[] adPositions = {0, 3, 6};
        int ai = 0, ni = 0;
        int maxLen = natural.size() + ads.size();
        for (int pos = 0; pos < maxLen; pos++) {
            if (ai < ads.size() && contains(adPositions, pos)) {
                merged.add(ads.get(ai++));
            } else if (ni < natural.size()) {
                merged.add(natural.get(ni++));
            } else if (ai < ads.size()) {
                merged.add(ads.get(ai++));
            }
        }

        // 分页
        int total = merged.size();
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);
        return Map.of("records", merged.subList(from, to), "total", total, "pages", (total + size - 1) / size);
    }

    private boolean contains(int[] arr, int v) {
        for (int a : arr) if (a == v) return true;
        return false;
    }

    private List<Map<String, Object>> nearbyList(Double lng, Double lat, Integer categoryId, String sortType, int page, int size) {
        return (List<Map<String, Object>>) list(lng, lat, categoryId, null, sortType, page, size).get("records");
    }

    private Long UserContextUserId() {
        return com.waimai.common.context.UserContext.userId();
    }

    /** 用户坐标是否落在商家配送范围内；无坐标时不做限制（否则没定位就看不到任何商家） */
    private boolean inDeliveryRange(Merchant m, Double lng, Double lat) {
        Double km = rawDistanceKm(m, lng, lat);
        return pricingService.inRange(m.getDeliveryRadiusKm(), km);
    }

    /** 商家 → 用户坐标的直线距离（km）；任一方缺坐标返回 null */
    private Double rawDistanceKm(Merchant m, Double lng, Double lat) {
        if (lng == null || lat == null || m.getLng() == null || m.getLat() == null) return null;
        return GeoUtil.distanceKm(lng, lat, m.getLng().doubleValue(), m.getLat().doubleValue());
    }

    /** 商家详情：商家 + 菜品分类 + 菜品 */
    public Map<String, Object> detail(Long merchantId) {
        Merchant m = merchantMapper.selectById(merchantId);
        if (m == null || m.getAuditStatus() != 1) throw new BizException("商家不存在或未过审");
        Map<String, Object> vo = toVo(m, null, null);
        List<DishCategory> cats = dishCategoryMapper.selectList(
                new QueryWrapper<DishCategory>().eq("merchant_id", merchantId).orderByAsc("sort"));
        List<Dish> dishes = dishMapper.selectList(new QueryWrapper<Dish>()
                .eq("merchant_id", merchantId).eq("status", 1).orderByDesc("is_recommend").orderByDesc("monthly_sales"));
        vo.put("categories", cats);
        vo.put("dishes", dishes);
        // 商家可用优惠券
        vo.put("coupons", couponBrief(merchantId));
        return vo;
    }

    private List<Map<String, Object>> couponBrief(Long merchantId) {
        List<Coupon> list = new CouponSelectHelper().select(merchantId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Coupon c : list) {
            result.add(Map.of("couponId", c.getId(), "name", c.getName(),
                    "type", c.getType(), "thresholdAmount", c.getThresholdAmount(),
                    "discountAmount", c.getDiscountAmount() == null ? 0 : c.getDiscountAmount(),
                    "discountRate", c.getDiscountRate() == null ? 0 : c.getDiscountRate()));
        }
        return result;
    }

    // 内部helper，避免直接注入CouponService造成循环依赖
    private class CouponSelectHelper {
        List<Coupon> select(Long merchantId) {
            return couponMapper.selectList(new QueryWrapper<Coupon>()
                    .eq("status", 1).in("merchant_id", 0L, merchantId)
                    .le("start_time", LocalDateTime.now()).ge("end_time", LocalDateTime.now()));
        }
    }

    private final CouponMapper couponMapper;

    /* ---------- 商户侧 ---------- */

    public Map<String, Object> shopInfo(Long merchantUserId) {
        Merchant m = merchantOfUser(merchantUserId);
        return toVo(m, null, null);
    }

    public void updateShop(Long merchantUserId, WebDTO.ShopUpdateReq req) {
        Merchant m = merchantOfUser(merchantUserId);
        if (req.getShopName() != null) m.setShopName(req.getShopName());
        if (req.getNotice() != null) m.setNotice(req.getNotice());
        if (req.getPhone() != null) m.setPhone(req.getPhone());
        if (req.getAddress() != null) m.setAddress(req.getAddress());
        if (req.getLogo() != null) m.setLogo(req.getLogo());
        if (req.getCategoryId() != null) m.setCategoryId(req.getCategoryId());
        if (req.getLng() != null) m.setLng(BigDecimal.valueOf(req.getLng()));
        if (req.getLat() != null) m.setLat(BigDecimal.valueOf(req.getLat()));
        if (req.getBusinessHours() != null) m.setBusinessHours(req.getBusinessHours());
        if (req.getMinOrderAmount() != null) m.setMinOrderAmount(BigDecimal.valueOf(req.getMinOrderAmount()));
        if (req.getDeliveryFee() != null) m.setDeliveryFee(BigDecimal.valueOf(req.getDeliveryFee()));
        // 配送范围：<=0 视为「清空」，回落到平台默认值
        if (req.getDeliveryRadiusKm() != null) {
            double r = req.getDeliveryRadiusKm();
            if (r <= 0) {
                m.setDeliveryRadiusKm(null);
            } else if (r < PricingService.MIN_RADIUS_KM || r > PricingService.MAX_RADIUS_KM) {
                throw new BizException("配送范围需在 " + PricingService.MIN_RADIUS_KM
                        + " ~ " + PricingService.MAX_RADIUS_KM + " km 之间");
            } else {
                m.setDeliveryRadiusKm(BigDecimal.valueOf(r));
            }
        }
        if (req.getPackageFee() != null) m.setPackageFee(BigDecimal.valueOf(req.getPackageFee()));
        if (req.getOpenStatus() != null) m.setOpenStatus(req.getOpenStatus());
        merchantMapper.updateById(m);
    }

    public Map<String, Object> dashboard(Long merchantUserId) {
        Merchant m = merchantOfUser(merchantUserId);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        List<Orders> today = ordersMapper.selectList(new QueryWrapper<Orders>()
                .eq("merchant_id", m.getId())
                .ge("created_at", todayStart)
                .in("status", "PAID", "ACCEPTED", "WAITING_PICKUP", "DELIVERING", "DELIVERED"));
        double gmv = today.stream().mapToDouble(o -> o.getPayAmount().doubleValue()).sum();
        long pending = ordersMapper.selectCount(new QueryWrapper<Orders>()
                .eq("merchant_id", m.getId()).eq("status", "PAID"));

        // 近7日趋势
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            List<Orders> dayOrders = ordersMapper.selectList(new QueryWrapper<Orders>()
                    .eq("merchant_id", m.getId())
                    .ge("created_at", d.atStartOfDay())
                    .lt("created_at", d.atTime(LocalTime.MAX))
                    .in("status", "PAID", "ACCEPTED", "WAITING_PICKUP", "DELIVERING", "DELIVERED"));
            trend.add(Map.of("date", d.toString(),
                    "amount", dayOrders.stream().mapToDouble(o -> o.getPayAmount().doubleValue()).sum(),
                    "count", dayOrders.size()));
        }

        // 热销菜品 Top5
        List<OrderItem> items = orderItemMapper.selectList(new QueryWrapper<OrderItem>()
                .inSql("order_id", "SELECT id FROM orders WHERE merchant_id=" + m.getId()
                        + " AND status IN ('PAID','ACCEPTED','WAITING_PICKUP','DELIVERING','DELIVERED')"));
        Map<String, Integer> dishCount = new LinkedHashMap<>();
        for (OrderItem oi : items) dishCount.merge(oi.getDishName(), oi.getQuantity(), Integer::sum);

        List<Map<String, Object>> hot = dishCount.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue()).limit(5)
                .map(e -> Map.<String, Object>of("dishName", e.getKey(), "count", e.getValue()))
                .toList();

        return Map.of("todayGmv", gmv, "todayOrders", today.size(), "pendingOrders", pending,
                "trend", trend, "hotDishes", hot);
    }

    public Merchant merchantOfUser(Long merchantUserId) {
        Merchant m = merchantMapper.selectOne(new QueryWrapper<Merchant>().eq("user_id", merchantUserId));
        if (m == null) throw new BizException(ResultCode.FORBIDDEN, "非商户账号");
        return m;
    }

    private Map<String, Object> toVo(Merchant m, Double lng, Double lat) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", m.getId());
        vo.put("shopName", m.getShopName());
        vo.put("categoryId", m.getCategoryId());
        MerchantCategory cat = categoryMapper.selectById(m.getCategoryId());
        vo.put("categoryName", cat == null ? "" : cat.getName());
        vo.put("logo", m.getLogo());
        vo.put("cover", m.getCover());
        vo.put("notice", m.getNotice());
        vo.put("phone", m.getPhone());
        vo.put("address", m.getAddress());
        vo.put("lng", m.getLng());
        vo.put("lat", m.getLat());
        vo.put("businessHours", m.getBusinessHours());
        vo.put("minOrderAmount", m.getMinOrderAmount());
        // 配送费按「当前用户 → 商家」的距离动态算；没有坐标时退回起步费
        Double km = rawDistanceKm(m, lng, lat);
        vo.put("deliveryFee", pricingService.deliveryFee(km == null ? 0 : km));
        vo.put("deliveryRadiusKm", pricingService.radiusOf(m.getDeliveryRadiusKm()));
        vo.put("packageFee", m.getPackageFee());
        vo.put("rating", m.getRating());
        vo.put("monthlySales", m.getMonthlySales());
        vo.put("openStatus", m.getOpenStatus());
        vo.put("auditStatus", m.getAuditStatus());
        if (km != null) {
            vo.put("distanceKm", Math.round(km * 10) / 10.0);
            vo.put("inRange", pricingService.inRange(m.getDeliveryRadiusKm(), km));
        } else {
            vo.put("distanceKm", 0);
            vo.put("inRange", true);
        }
        return vo;
    }
}
