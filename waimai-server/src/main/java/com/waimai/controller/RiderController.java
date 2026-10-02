package com.waimai.controller;

import com.waimai.common.annotation.RequireRole;
import com.waimai.common.context.UserContext;
import com.waimai.common.result.R;
import com.waimai.service.RiderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rider")
@RequiredArgsConstructor
public class RiderController {

    private final RiderService riderService;

    /** 骑手身份信息 */
    @RequireRole("RIDER")
    @GetMapping("/info")
    public R<?> info() {
        return R.ok(riderService.riderOf(UserContext.userId()));
    }

    /** 抢单大厅（L1） */
    @RequireRole("RIDER")
    @GetMapping("/hall")
    public R<List<Map<String, Object>>> hall() {
        return R.ok(riderService.grabHall());
    }

    /** 我的配送 */
    @RequireRole("RIDER")
    @GetMapping("/deliveries")
    public R<List<Map<String, Object>>> deliveries() {
        return R.ok(riderService.myDeliveries(riderService.riderIdOf(UserContext.userId())));
    }

    /** 接单 */
    @RequireRole("RIDER")
    @PostMapping("/order/{id}/grab")
    public R<Void> grab(@PathVariable Long id) {
        riderService.grab(UserContext.userId(), id);
        return R.ok();
    }

    /** 取餐 */
    @RequireRole("RIDER")
    @PostMapping("/order/{id}/pickup")
    public R<Void> pickup(@PathVariable Long id) {
        riderService.pickup(UserContext.userId(), id);
        return R.ok();
    }

    /** 送达 */
    @RequireRole("RIDER")
    @PostMapping("/order/{id}/deliver")
    public R<Void> deliver(@PathVariable Long id) {
        riderService.deliver(UserContext.userId(), id);
        return R.ok();
    }

    /** 位置上报（L3，真实 GPS / 模拟骑行共用） */
    @RequireRole("RIDER")
    @PostMapping("/location")
    public R<Void> reportLocation(@RequestParam double lng,
                                  @RequestParam double lat,
                                  @RequestParam(required = false) Long orderId) {
        riderService.reportLocation(UserContext.userId(), lng, lat, orderId);
        return R.ok();
    }
}
