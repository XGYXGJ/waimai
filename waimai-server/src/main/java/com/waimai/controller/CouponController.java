package com.waimai.controller;

import com.waimai.common.context.UserContext;
import com.waimai.common.result.R;
import com.waimai.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /** 领取优惠券 */
    @PostMapping("/{id}/receive")
    public R<Void> receive(@PathVariable Long id) {
        couponService.receive(UserContext.userId(), id);
        return R.ok();
    }

    /** 我的优惠券 */
    @GetMapping("/my")
    public R<Map<String, Object>> my(@RequestParam(required = false) Integer status) {
        return R.ok(couponService.myCoupons(UserContext.userId(), status));
    }

    /** 某商家下单可用的券 */
    @GetMapping("/usable")
    public R<?> usable(@RequestParam Long merchantId,
                       @RequestParam java.math.BigDecimal dishAmount) {
        return R.ok(couponService.usable(UserContext.userId(), merchantId, dishAmount));
    }
}
