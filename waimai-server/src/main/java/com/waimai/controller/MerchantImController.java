package com.waimai.controller;

import java.util.List;
import java.util.Map;

import com.waimai.common.annotation.RequireRole;
import com.waimai.common.context.UserContext;
import com.waimai.common.result.R;
import com.waimai.dto.WebDTO;
import com.waimai.service.ImService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 商户端：顾客会话与售后工单处理（/api/merchant/im） */
@RestController
@RequestMapping("/api/merchant/im")
@RequireRole("MERCHANT")
@RequiredArgsConstructor
public class MerchantImController {

    private final ImService imService;

    @GetMapping("/sessions")
    public R<List<Map<String, Object>>> sessions() {
        return R.ok(imService.merchantSessions(UserContext.userId()));
    }

    @GetMapping("/session/{id}/messages")
    public R<Map<String, Object>> messages(@PathVariable Long id,
                                           @RequestParam(required = false) Long sinceId) {
        Long merchantId = imService.merchantIdOf(UserContext.userId());
        return R.ok(imService.messages(id, sinceId, ImService.SIDE_MERCHANT, merchantId));
    }

    /** 发消息：文本 / 图片 / 工单卡片（答复售后） */
    @PostMapping("/session/{id}/send")
    public R<Map<String, Object>> send(@PathVariable Long id, @RequestBody WebDTO.ImSendReq req) {
        Long merchantId = imService.merchantIdOf(UserContext.userId());
        return R.ok(imService.send(id, ImService.SIDE_MERCHANT, merchantId, req));
    }

    /** 工单列表：status 为空查全部 */
    @GetMapping("/tickets")
    public R<List<Map<String, Object>>> tickets(@RequestParam(required = false) String status) {
        return R.ok(imService.merchantTickets(UserContext.userId(), status));
    }

    /** 处理工单：PROCESSING / APPROVED / REJECTED / CLOSED */
    @PostMapping("/ticket/{id}/handle")
    public R<Map<String, Object>> handle(@PathVariable Long id,
                                         @RequestBody WebDTO.ImTicketHandleReq req) {
        return R.ok(imService.handleTicket(UserContext.userId(), id, req));
    }
}
