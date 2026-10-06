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

    /** 骑手身份信息（含在途单数、今日单数、是否在线） */
    @RequireRole("RIDER")
    @GetMapping("/info")
    public R<?> info() {
        return R.ok(riderService.riderProfile(UserContext.userId()));
    }

    /** 上下班切换 */
    @RequireRole("RIDER")
    @PostMapping("/online")
    public R<Void> online(@RequestParam boolean value) {
        riderService.setOnline(UserContext.userId(), value);
        return R.ok();
    }

    /** 抢单大厅（L1）：带配送费与「骑手当前位置 → 商家」直线距离，按距离由近到远 */
    @RequireRole("RIDER")
    @GetMapping("/hall")
    public R<List<Map<String, Object>>> hall() {
        return R.ok(riderService.grabHall(UserContext.userId()));
    }

    /** 我的配送（进行中 + 已送达） */
    @RequireRole("RIDER")
    @GetMapping("/deliveries")
    public R<List<Map<String, Object>>> deliveries() {
        return R.ok(riderService.myDeliveries(riderService.riderIdOf(UserContext.userId())));
    }

    /** 历史订单：只含已送达。与 /deliveries 分开，避免两个 tab 显示同一份数据 */
    @RequireRole("RIDER")
    @GetMapping("/history")
    public R<List<Map<String, Object>>> history() {
        return R.ok(riderService.myHistory(riderService.riderIdOf(UserContext.userId())));
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

    /** 位置上报（L3，真实 GPS / 模拟骑行共用）。orderId 可选，带上时校验归属 */
    @RequireRole("RIDER")
    @PostMapping("/location")
    public R<Void> reportLocation(@RequestParam double lng,
                                  @RequestParam double lat,
                                  @RequestParam(required = false) Long orderId) {
        riderService.reportLocation(UserContext.userId(), lng, lat, orderId);
        return R.ok();
    }
}
