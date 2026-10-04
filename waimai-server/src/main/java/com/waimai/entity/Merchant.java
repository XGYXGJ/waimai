package com.waimai.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("merchant")
public class Merchant {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String shopName;

    private Integer categoryId;

    private String logo;

    private String cover;

    private String notice;

    private String phone;

    private String address;

    private BigDecimal lng;

    private BigDecimal lat;

    private String businessHours;

    private BigDecimal minOrderAmount;

    private BigDecimal deliveryFee;

    /** 配送范围（km）；null = 使用平台默认（sys_config: delivery.radius_km） */
    private BigDecimal deliveryRadiusKm;

    private BigDecimal packageFee;

    private BigDecimal rating;

    private Integer monthlySales;

    private Integer openStatus;

    private Integer auditStatus;

    private String auditRemark;

    private LocalDateTime createdAt;
}