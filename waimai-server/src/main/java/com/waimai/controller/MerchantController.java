package com.waimai.controller;

import com.waimai.common.annotation.PublicApi;
import com.waimai.common.annotation.RequireRole;
import com.waimai.common.context.UserContext;
import com.waimai.common.result.R;
import com.waimai.dto.WebDTO;
import com.waimai.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;
    private final DishService dishService;
    private final CouponService couponService;
    private final BidService bidService;
    private final AiService aiService;

    /* ---------- 用户端：商家浏览（公开） ---------- */

    @PublicApi
    @GetMapping("/home")
    public R<Map<String, Object>> home(@RequestParam(required = false) Double lng,
                                       @RequestParam(required = false) Double lat) {
        return R.ok(merchantService.home(lng, lat));
    }

    @PublicApi
    @GetMapping("/list")
    public R<Map<String, Object>> list(@RequestParam(required = false) Double lng,
                                       @RequestParam(required = false) Double lat,
                                       @RequestParam(required = false) Integer categoryId,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String sort,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        return R.ok(merchantService.list(lng, lat, categoryId, keyword, sort, page, size));
    }

    @PublicApi
    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(merchantService.detail(id));
    }

    @PublicApi
    @GetMapping("/search-dishes")
    public R<Map<String, Object>> searchDishes(@RequestParam String keyword) {
        return R.ok(dishService.searchDishes(keyword));
    }

    @PublicApi
    @GetMapping("/{id}/coupons")
    public R<?> coupons(@PathVariable Long id) {
        return R.ok(couponService.available(id));
    }

    /** 商户经营分类列表（公开，供商户端入驻/店铺设置选择） */
    @PublicApi
    @GetMapping("/category-options")
    public R<?> categoryOptions() {
        return R.ok(merchantService.categoryOptions());
    }

    /* ---------- 商户端：店铺管理 ---------- */

    @RequireRole("MERCHANT")
    @GetMapping("/shop/info")
    public R<Map<String, Object>> shopInfo() {
        return R.ok(merchantService.shopInfo(UserContext.userId()));
    }

    @RequireRole("MERCHANT")
    @PutMapping("/shop")
    public R<Void> updateShop(@RequestBody WebDTO.ShopUpdateReq req) {
        merchantService.updateShop(UserContext.userId(), req);
        return R.ok();
    }

    @RequireRole("MERCHANT")
    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        return R.ok(merchantService.dashboard(UserContext.userId()));
    }

    /* ---------- 商户端：菜品分类 ---------- */

    @RequireRole("MERCHANT")
    @GetMapping("/categories")
    public R<?> categories() {
        return R.ok(dishService.categories(UserContext.userId()));
    }

    @RequireRole("MERCHANT")
    @PostMapping("/category")
    public R<Void> saveCategory(@RequestParam(required = false) Long id,
                                @RequestParam String name,
                                @RequestParam(required = false) Integer sort) {
        dishService.saveCategory(UserContext.userId(), id, name, sort);
        return R.ok();
    }

    @RequireRole("MERCHANT")
    @DeleteMapping("/category/{id}")
    public R<Void> deleteCategory(@PathVariable Long id) {
        dishService.deleteCategory(UserContext.userId(), id);
        return R.ok();
    }

    /* ---------- 商户端：菜品管理 ---------- */

    @RequireRole("MERCHANT")
    @GetMapping("/dishes")
    public R<Map<String, Object>> dishes(@RequestParam(required = false) Long categoryId) {
        return R.ok(dishService.dishes(UserContext.userId(), categoryId));
    }

    @RequireRole("MERCHANT")
    @PostMapping("/dish")
    public R<Void> saveDish(@RequestBody WebDTO.DishReq req) {
        dishService.save(UserContext.userId(), req);
        return R.ok();
    }

    @RequireRole("MERCHANT")
    @PostMapping("/dish/{id}/status")
    public R<Void> changeDishStatus(@PathVariable Long id, @RequestParam Integer status) {
        dishService.changeStatus(UserContext.userId(), id, status);
        return R.ok();
    }

    /* ---------- 商户端：优惠券 ---------- */

    @RequireRole("MERCHANT")
    @GetMapping("/coupons")
    public R<Map<String, Object>> merchantCoupons() {
        return R.ok(couponService.merchantCoupons(merchantId()));
    }

    @RequireRole("MERCHANT")
    @PostMapping("/coupon")
    public R<Void> createCoupon(@RequestBody WebDTO.CouponReq req) {
        couponService.create(merchantId(), req);
        return R.ok();
    }

    @RequireRole("MERCHANT")
    @PostMapping("/coupon/{id}/off")
    public R<Void> offCoupon(@PathVariable Long id) {
        couponService.offShelf(merchantId(), id);
        return R.ok();
    }

    /* ---------- 商户端：竞价排名 ---------- */

    @RequireRole("MERCHANT")
    @GetMapping("/campaigns")
    public R<?> campaigns() {
        return R.ok(bidService.myCampaigns(UserContext.userId()));
    }

    @RequireRole("MERCHANT")
    @PostMapping("/campaign")
    public R<Void> createCampaign(@RequestBody WebDTO.BidCampaignReq req) {
        bidService.createCampaign(UserContext.userId(), req);
        return R.ok();
    }

    @RequireRole("MERCHANT")
    @PostMapping("/campaign/{id}/status")
    public R<Void> campaignStatus(@PathVariable Long id, @RequestParam Integer status) {
        bidService.updateStatus(UserContext.userId(), id, status);
        return R.ok();
    }

    /* ---------- 商户端：销量预测 ---------- */

    @RequireRole("MERCHANT")
    @GetMapping("/forecast")
    public R<Map<String, Object>> forecast() {
        return R.ok(aiService.forecast(UserContext.userId()));
    }

    private Long merchantId() {
        return merchantService.merchantOfUser(UserContext.userId()).getId();
    }
}
