package com.waimai.controller;

import com.waimai.common.annotation.PublicApi;
import com.waimai.common.context.UserContext;
import com.waimai.common.result.R;
import com.waimai.service.BidService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common")
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;

    /** 广告点击回传（CPC 计费 + 行为埋点），匿名用户也可能触发 */
    @PublicApi
    @PostMapping("/ad-click")
    public R<Void> adClick(@RequestParam Long campaignId,
                           @RequestParam(required = false) Integer position) {
        bidService.log(campaignId, UserContext.userId(), position, 1);
        return R.ok();
    }
}
