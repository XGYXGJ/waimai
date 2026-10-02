package com.waimai.controller;

import com.waimai.common.context.UserContext;
import com.waimai.common.result.R;
import com.waimai.dto.WebDTO;
import com.waimai.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /** AI 智能客服 */
    @PostMapping("/chat")
    public R<Map<String, Object>> chat(@RequestBody WebDTO.ChatSendReq req) {
        return R.ok(aiService.chat(UserContext.userId(), req));
    }
}
