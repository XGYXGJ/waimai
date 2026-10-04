package com.waimai.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("orders")
public class Orders {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    private Long merchantId;

    private Long riderId;

    private String addressSnapshot;

    private BigDecimal dishAmount;

    private BigDecimal deliveryFee;

    private BigDecimal packageFee;

    private BigDecimal discountAmount;

    private BigDecimal payAmount;

    private Long userCouponId;

    /** 下单幂等令牌：由客户端生成，同一个令牌只会落一笔订单（防连点 / 弱网重试） */
    private String clientToken;

    /** 商家到收货地址的直线距离（km），下单时快照 */
    private BigDecimal distanceKm;

    /** 分账费率快照：下单那一刻的抽成比例，事后改费率不影响历史订单 */
    private BigDecimal merchantRate;

    private BigDecimal riderRate;

    private BigDecimal merchantIncome;

    private BigDecimal riderIncome;

    private BigDecimal platformIncome;

    private String status;

    private String remark;

    private LocalDateTime payTime;

    private LocalDateTime acceptTime;

    private LocalDateTime pickupTime;

    private LocalDateTime deliveredTime;

    private String cancelReason;

    private String cancelBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}