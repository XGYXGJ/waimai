package com.waimai.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("address")
public class Address {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String contact;

    private String phone;

    private Integer gender;

    private String province;

    private String city;

    private String district;

    private String detail;

    private BigDecimal lng;

    private BigDecimal lat;

    private String tag;

    private Integer isDefault;

    private LocalDateTime createdAt;
}