package com.waimai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.waimai.common.exception.BizException;
import com.waimai.common.result.ResultCode;
import com.waimai.dto.WebDTO;
import com.waimai.entity.Orders;
import com.waimai.entity.Review;
import com.waimai.mapper.OrdersMapper;
import com.waimai.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 评价：提交（完成后）、商家详情页评价列表、我的评价、商家回复。
 * 提交后异步触发情感分析，负评（NEG 且置信度高）进管理端待处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrdersMapper ordersMapper;
    private final AiService aiService;
    private final MerchantService merchantService;

    public void submit(Long userId, Long orderId, WebDTO.ReviewReq req) {
        Orders o = ordersMapper.selectById(orderId);
        if (o == null || !o.getUserId().equals(userId)) throw new BizException("订单不存在");
        if (!"DELIVERED".equals(o.getStatus())) throw new BizException("订单未完成，无法评价");
        Long existing = reviewMapper.selectCount(new QueryWrapper<Review>().eq("order_id", orderId));
        if (existing > 0) throw new BizException("该订单已评价");

        Review r = new Review();
        r.setOrderId(orderId);
        r.setUserId(userId);
        r.setMerchantId(o.getMerchantId());
        r.setRating(req.getRating() == null ? 5 : req.getRating());
        r.setContent(req.getContent());
        r.setImages(req.getImages());
        reviewMapper.insert(r);

        // 异步情感分析
        aiService.analyzeSentimentAsync(r.getId());
    }

    /** 商家详情页评价列表 */
    public List<Review> merchantReviews(Long merchantId, int limit) {
        return reviewMapper.selectList(new QueryWrapper<Review>()
                .eq("merchant_id", merchantId)
                .orderByDesc("created_at").last("limit " + limit));
    }

    /** 我的评价 */
    public List<Review> myReviews(Long userId) {
        return reviewMapper.selectList(new QueryWrapper<Review>()
                .eq("user_id", userId).orderByDesc("created_at"));
    }

    /** 商家回复 */
    public void reply(Long merchantUserId, Long reviewId, String reply) {
        Long merchantId = merchantService.merchantOfUser(merchantUserId).getId();
        Review r = reviewMapper.selectById(reviewId);
        if (r == null || !r.getMerchantId().equals(merchantId)) throw new BizException("评价不存在");
        r.setReply(reply);
        reviewMapper.updateById(r);
    }

    /** 管理端：负面评价待处理列表 */
    public List<Review> negativeReviews(int limit) {
        return reviewMapper.selectList(new QueryWrapper<Review>()
                .eq("sentiment", "NEG")
                .isNull("reply")
                .orderByDesc("created_at").last("limit " + limit));
    }
}
