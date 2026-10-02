package com.waimai.controller;

import com.waimai.common.annotation.PublicApi;
import com.waimai.common.context.UserContext;
import com.waimai.common.result.R;
import com.waimai.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final AiService aiService;

    /** 首页「AI 每日推荐」：6 个菜品 + AI 推荐理由 */
    @PublicApi
    @GetMapping("/daily")
    public R<Map<String, Object>> daily(@RequestParam(required = false) Double lng,
                                        @RequestParam(required = false) Double lat) {
        return R.ok(aiService.dailyRecommend(UserContext.userId(), lng, lat));
    }
}
