package com.waimai.controller;

import com.waimai.common.annotation.RequireRole;
import com.waimai.common.result.R;
import com.waimai.dto.WebDTO;
import com.waimai.service.AdminService;
import com.waimai.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RequireRole("ADMIN")
public class AdminController {

    private final AdminService adminService;
    private final OperationLogService operationLogService;

    /* ---------- 商户 ---------- */

    @GetMapping("/merchants")
    public R<Map<String, Object>> merchants(@RequestParam(required = false) Integer auditStatus,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return R.ok(adminService.merchants(auditStatus, page, size));
    }

    @PutMapping("/merchants/{id}/audit")
    public R<Void> auditMerchant(@PathVariable Long id, @RequestBody WebDTO.AuditReq req) {
        adminService.auditMerchant(id, req.getPass(), req.getRemark());
        return R.ok();
    }

    /* ---------- 骑手 ---------- */

    @GetMapping("/riders")
    public R<Map<String, Object>> riders(@RequestParam(required = false) Integer auditStatus,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        return R.ok(adminService.riders(auditStatus, page, size));
    }

    @PutMapping("/riders/{id}/audit")
    public R<Void> auditRider(@PathVariable Long id, @RequestBody WebDTO.AuditReq req) {
        adminService.auditRider(id, req.getPass());
        return R.ok();
    }

    /* ---------- 用户 ---------- */

    @GetMapping("/users")
    public R<Map<String, Object>> users(@RequestParam(required = false) String role,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        return R.ok(adminService.users(role, page, size));
    }

    @PutMapping("/users/{id}/status")
    public R<Void> userStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminService.changeUserStatus(id, status);
        return R.ok();
    }

    /* ---------- 订单 ---------- */

    @GetMapping("/orders")
    public R<Map<String, Object>> orders(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        return R.ok(adminService.orders(keyword, status, page, size));
    }

    @PutMapping("/orders/{id}/refund")
    public R<Void> refund(@PathVariable Long id) {
        adminService.refund(id);
        return R.ok();
    }

    /* ---------- 评价 ---------- */

    @GetMapping("/reviews")
    public R<Map<String, Object>> reviews(@RequestParam(required = false) Long merchantId,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        return R.ok(adminService.reviews(merchantId, page, size));
    }

    @DeleteMapping("/reviews/{id}")
    public R<Void> deleteReview(@PathVariable Long id) {
        adminService.deleteReview(id);
        return R.ok();
    }

    /* ---------- 竞价审核 ---------- */

    @PutMapping("/bid-campaigns/{id}/audit")
    public R<Void> auditCampaign(@PathVariable Long id, @RequestBody WebDTO.AuditReq req) {
        adminService.auditCampaign(id, req.getPass());
        return R.ok();
    }

    /* ---------- 数据大屏 ---------- */

    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        return R.ok(adminService.dashboard());
    }

    /* ---------- 收入结算 ---------- */

    /** 收入总览：今日/近7天/本月/累计 的 GMV 与平台、商家、骑手三方收入 */
    @GetMapping("/income/overview")
    public R<Map<String, Object>> incomeOverview() {
        return R.ok(adminService.incomeOverview());
    }

    /** 按商家汇总收入明细 */
    @GetMapping("/income/merchants")
    public R<Map<String, Object>> incomeByMerchant(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return R.ok(adminService.incomeByMerchant(page, size));
    }

    /* ---------- 操作日志 ---------- */

    /** 管理端操作流水：审核/退款/封禁/删评的留痕（设计文档第 9 章） */
    @GetMapping("/operation-logs")
    public R<Map<String, Object>> operationLogs(@RequestParam(required = false) String action,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return R.ok(operationLogService.page(action, page, size));
    }
}
