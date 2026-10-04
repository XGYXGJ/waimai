package com.waimai.controller;

import java.util.List;
import java.util.Map;

import com.waimai.common.context.UserContext;
import com.waimai.common.result.R;
import com.waimai.dto.WebDTO;
import com.waimai.service.ImService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 用户端：订单会话与售后工单（/api/im） */
@RestController
@RequestMapping("/api/im")
@RequiredArgsConstructor
public class ImController {

    private final ImService imService;

    /** 我的会话列表 */
    @GetMapping("/sessions")
    public R<List<Map<String, Object>>> sessions() {
        return R.ok(imService.mySessions(UserContext.userId()));
    }

    /** 按订单开启（或获取）会话，返回会话详情 */
    @PostMapping("/session/open")
    public R<Map<String, Object>> open(@RequestBody WebDTO.ImOpenReq req) {
        return R.ok(imService.openSession(UserContext.userId(), req.getOrderId()));
    }

    /** 会话消息：sinceId 增量拉取，进入会话即已读 */
    @GetMapping("/session/{id}/messages")
    public R<Map<String, Object>> messages(@PathVariable Long id,
                                           @RequestParam(required = false) Long sinceId) {
        return R.ok(imService.messages(id, sinceId, true, UserContext.userId()));
    }

    /** 发消息：文本 / 图片 / 订单卡片 */
    @PostMapping("/session/{id}/send")
    public R<Map<String, Object>> send(@PathVariable Long id, @RequestBody WebDTO.ImSendReq req) {
        return R.ok(imService.send(id, "USER", UserContext.userId(), req));
    }

    /** 发起售后工单（退款 / 赔偿 / 补发 / 其他） */
    @PostMapping("/session/{id}/ticket")
    public R<Map<String, Object>> createTicket(@PathVariable Long id,
                                               @RequestBody WebDTO.ImTicketReq req) {
        return R.ok(imService.createTicket(UserContext.userId(), id, req));
    }

    /** 我的工单列表 */
    @GetMapping("/tickets")
    public R<List<Map<String, Object>>> tickets() {
        return R.ok(imService.myTickets(UserContext.userId()));
    }

    @GetMapping("/ticket/{id}")
    public R<Map<String, Object>> ticket(@PathVariable Long id) {
        return R.ok(imService.ticketDetail(id, UserContext.userId()));
    }
}
