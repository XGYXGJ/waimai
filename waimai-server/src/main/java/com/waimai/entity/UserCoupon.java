package com.waimai.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user_coupon")
public class UserCoupon {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long couponId;

    private Long userId;

    private Integer status;

    private LocalDateTime receivedAt;

    private LocalDateTime usedAt;

    private Long orderId;
}