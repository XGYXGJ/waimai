package com.waimai.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("dish")
public class Dish {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    private Long categoryId;

    private String name;

    private String description;

    private String image;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private String unit;

    private Integer stock;

    private Integer monthlySales;

    private BigDecimal rating;

    private String tags;

    private Integer isRecommend;

    private Integer status;

    private LocalDateTime createdAt;
}