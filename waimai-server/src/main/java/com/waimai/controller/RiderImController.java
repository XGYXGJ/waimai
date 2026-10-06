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

/**
 * 骑手端：订单三方会话（/api/rider/im）。
 *
 * <p>骑手只能发文本与图片：工单涉及退款与赔付，必须由商家处置，
 * 骑手只读，避免出现「骑手答应赔了但商家没记账」的对不上账。
 */
@RestController
@RequestMapping("/api/rider/im")
@RequireRole("RIDER")
@RequiredArgsConstructor
public class RiderImController {

    private final ImService imService;

    /** 我的会话（仅自己配送过、且仍在沟通有效期内的订单） */
    @GetMapping("/sessions")
    public R<List<Map<String, Object>>> sessions() {
        return R.ok(imService.riderSessions(UserContext.userId()));
    }

    /** 按订单开启（或获取）会话 */
    @PostMapping("/session/open")
    public R<Map<String, Object>> open(@RequestBody WebDTO.ImOpenReq req) {
        return R.ok(imService.riderOpenSession(UserContext.userId(), req.getOrderId()));
    }

    /** 会话消息：sinceId 增量拉取，进入会话即已读 */
    @GetMapping("/session/{id}/messages")
    public R<Map<String, Object>> messages(@PathVariable Long id,
                                           @RequestParam(required = false) Long sinceId) {
        return R.ok(imService.messages(id, sinceId, ImService.SIDE_RIDER,
                imService.riderIdOf(UserContext.userId())));
    }

    /** 发消息：文本 / 图片 */
    @PostMapping("/session/{id}/send")
    public R<Map<String, Object>> send(@PathVariable Long id, @RequestBody WebDTO.ImSendReq req) {
        return R.ok(imService.send(id, ImService.SIDE_RIDER,
                imService.riderIdOf(UserContext.userId()), req));
    }

    /** 相关工单（只读） */
    @GetMapping("/tickets")
    public R<List<Map<String, Object>>> tickets() {
        return R.ok(imService.riderTickets(UserContext.userId()));
    }
}
