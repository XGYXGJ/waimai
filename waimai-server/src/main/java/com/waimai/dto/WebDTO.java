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
        private Double packageFee;
        private Integer openStatus;
    }
}
