package com.waimai.dto;

import lombok.Data;

/** 全部请求 DTO（嵌套静态类）。复合响应视图统一用 Map 返回。 */
public class WebDTO {

    @Data
    public static class RegisterReq {
        private String phone;
        private String password;
        private String nickname;
        private String role; // USER / MERCHANT / RIDER
    }

    @Data
    public static class LoginReq {
        private String phone;
        private String password;
    }

    @Data
    public static class RefreshReq {
        private String refreshToken;
    }

    @Data
    public static class OrderCreateReq {
        private Long merchantId;
        private Long addressId;
        private Long userCouponId; // 可空
        private String remark;
        /**
         * 下单幂等令牌（客户端生成，同一个「下单意图」复用同一个值）。
         * 连点 / 弱网重试时后端只会落一笔订单；不传则退化为无幂等保护。
         */
        private String clientToken;
    }

    @Data
    public static class AddressReq {
        private Long id;
        private String contact;
        private String phone;
        private Integer gender;
        private String province;
        private String city;
        private String district;
        private String detail;
        private Double lng;
        private Double lat;
        private String tag;
        private Integer isDefault;
    }

    @Data
    public static class DishReq {
        private Long id;
        private Long categoryId;
        private String name;
        private String description;
        private String image;
        private Double price;
        private Double originalPrice;
        private String unit;
        private Integer stock;
        private String tags;
        private Integer isRecommend;
        private Integer status;
    }

    @Data
    public static class CouponReq {
        private String name;
        private Integer type;
        private Double thresholdAmount;
        private Double discountAmount;
        private Double discountRate;
        private Integer totalCount;
        private Integer perUserLimit;
        private String startTime;
        private String endTime;
    }

    @Data
    public static class BidCampaignReq {
        private String keyword;
        private Double bid;
        private Double dailyBudget;
        private String startDate;
        private String endDate;
    }

    @Data
    public static class ReviewReq {
        private Integer rating;
        private String content;
        private String images;
    }

    @Data
    public static class AuditReq {
        private Boolean pass;
        private String remark;
    }

    @Data
    public static class ChatSendReq {
        private String content;
    }

    @Data
    public static class ConfigUpdateReq {
        private String configValue;
    }

    @Data
    public static class AiModelSaveReq {
        private Long id;
        private String name;
        private String provider;   // zen / openai / ollama
        private String baseUrl;
        private String apiKey;
        private String modelId;
        private Integer enabled;
        private Integer priority;
        private Integer timeout;
        private String remark;
    }

    /* ---------- 订单会话 / 售后工单 ---------- */

    /** 发起会话（按订单，已存在则直接返回） */
    @Data
    public static class ImOpenReq {
        private Long orderId;
    }

    /** 发送消息：TEXT / IMAGE / ORDER */
    @Data
    public static class ImSendReq {
        private String msgType;    // TEXT 文本 / IMAGE 图片 / ORDER 订单卡片
        private String content;    // 文本正文或图片说明
        private java.util.List<String> images; // msgType=IMAGE 时的图片 URL 列表
    }

    /** 发起售后工单：退款 / 赔偿 / 补发 / 其他 */
    @Data
    public static class ImTicketReq {
        private String type;                 // REFUND / COMPENSATE / REISSUE / OTHER
        private java.math.BigDecimal amount; // 申请金额
        private String reason;               // 原因
        private java.util.List<String> images; // 凭证图片
    }

    /** 商家处理工单 */
    @Data
    public static class ImTicketHandleReq {
        private String action;   // PROCESSING / APPROVED / REJECTED / CLOSED
        private String reply;    // 处理意见
    }

    @Data
    public static class ShopUpdateReq {
        private String shopName;
        private String notice;
        private String phone;
        private String address;
        private String logo;
        private Integer categoryId;
        private Double lng;
        private Double lat;
        private String businessHours;
        private Double minOrderAmount;
        private Double deliveryFee;
        /** 配送范围（km）；不传 = 沿用平台默认，传 0 视为「清空为默认」由服务端处理 */
        private Double deliveryRadiusKm;
        private Double packageFee;
        private Integer openStatus;
    }
}
