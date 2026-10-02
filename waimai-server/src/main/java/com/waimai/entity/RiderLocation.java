package com.waimai.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("rider_location")
public class RiderLocation {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long riderId;

    private Long orderId;

    private BigDecimal lng;

    private BigDecimal lat;

    private LocalDateTime createdAt;
}