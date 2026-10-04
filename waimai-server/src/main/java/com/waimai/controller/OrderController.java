package com.waimai.controller;

import com.waimai.common.annotation.RequireRole;
import com.waimai.common.context.UserContext;
import com.waimai.common.result.R;
import com.waimai.dto.WebDTO;
import com.waimai.service.OrderService;
import com.waimai.service.RiderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final RiderService riderService;

    /* ---------- 用户端 ---------- */

    @GetMapping("/preview")
    public R<Map<String, Object>> preview(@RequestParam Long merchantId,
                                          @RequestParam(required = false) Long addressId) {
        return R.ok(orderService.preview(UserContext.userId(), merchantId, addressId));
    }

    @PostMapping
    public R<Map<String, Object>> create(@RequestBody WebDTO.OrderCreateReq req) {
        return R.ok(orderService.create(UserContext.userId(), req));
    }

    @PostMapping("/{id}/pay")
    public R<Void> pay(@PathVariable Long id) {
        orderService.pay(UserContext.userId(), id);
        return R.ok();
    }

    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        orderService.cancelByUser(UserContext.userId(), id);
        return R.ok();
    }

    @GetMapping("/my")
    public R<Map<String, Object>> my(@RequestParam(required = false) String status,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "10") int size) {
        return R.ok(orderService.myOrders(UserContext.userId(), status, page, size));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(orderService.detail(UserContext.userId(), id));
    }

    /* ---------- 商户端 ---------- */

    @RequireRole("MERCHANT")
    @GetMapping("/merchant")
    public R<Map<String, Object>> merchantOrders(@RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return R.ok(orderService.merchantOrders(UserContext.userId(), status, page, size));
    }

    @RequireRole("MERCHANT")
    @PostMapping("/{id}/accept")
    public R<Void> accept(@PathVariable Long id) {
        orderService.accept(UserContext.userId(), id);
        return R.ok();
    }

    @RequireRole("MERCHANT")
    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestParam(required = false) String reason) {
        orderService.reject(UserContext.userId(), id, reason);
        return R.ok();
    }

    @RequireRole("MERCHANT")
    @PostMapping("/{id}/ready")
    public R<Void> ready(@PathVariable Long id) {
        orderService.ready(UserContext.userId(), id);
        return R.ok();
    }

    /* ---------- 骑手端 ---------- */

    @RequireRole("RIDER")
    @PostMapping("/{id}/grab")
    public R<Void> grab(@PathVariable Long id) {
        riderService.grab(UserContext.userId(), id);
        return R.ok();
    }

    @RequireRole("RIDER")
    @PostMapping("/{id}/pickup")
    public R<Void> pickup(@PathVariable Long id) {
        riderService.pickup(UserContext.userId(), id);
        return R.ok();
    }

    @RequireRole("RIDER")
    @PostMapping("/{id}/deliver")
    public R<Void> deliver(@PathVariable Long id) {
        riderService.deliver(UserContext.userId(), id);
        return R.ok();
    }
}
