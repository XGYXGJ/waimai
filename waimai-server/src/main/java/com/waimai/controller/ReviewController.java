package com.waimai.controller;

import com.waimai.common.annotation.PublicApi;
import com.waimai.common.annotation.RequireRole;
import com.waimai.common.context.UserContext;
import com.waimai.common.result.R;
import com.waimai.dto.WebDTO;
import com.waimai.entity.Review;
import com.waimai.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** 提交评价（订单完成后） */
    @PostMapping("/{orderId}")
    public R<Void> submit(@PathVariable Long orderId, @RequestBody WebDTO.ReviewReq req) {
        reviewService.submit(UserContext.userId(), orderId, req);
        return R.ok();
    }

    /** 商家详情页评价列表（匿名可看） */
    @PublicApi
    @GetMapping("/merchant/{merchantId}")
    public R<List<Review>> merchantReviews(@PathVariable Long merchantId) {
        return R.ok(reviewService.merchantReviews(merchantId, 20));
    }

    /** 我的评价 */
    @GetMapping("/my")
    public R<List<Review>> my() {
        return R.ok(reviewService.myReviews(UserContext.userId()));
    }

    /** 商家回复 */
    @RequireRole("MERCHANT")
    @PostMapping("/{reviewId}/reply")
    public R<Void> reply(@PathVariable Long reviewId, @RequestParam String reply) {
        reviewService.reply(UserContext.userId(), reviewId, reply);
        return R.ok();
    }

    /** 管理端：负面评价待处理 */
    @RequireRole("ADMIN")
    @GetMapping("/negative")
    public R<List<Review>> negative() {
        return R.ok(reviewService.negativeReviews(50));
    }
}
