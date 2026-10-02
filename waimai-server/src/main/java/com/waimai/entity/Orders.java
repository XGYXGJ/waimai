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