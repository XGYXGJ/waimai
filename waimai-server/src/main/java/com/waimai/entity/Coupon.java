package com.waimai.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("coupon")
public class Coupon {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    private String name;

    private Integer type;

    private BigDecimal thresholdAmount;

    private BigDecimal discountAmount;

    private BigDecimal discountRate;

    private Integer totalCount;

    private Integer receivedCount;

    private Integer perUserLimit;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer status;

    private LocalDateTime createdAt;
}