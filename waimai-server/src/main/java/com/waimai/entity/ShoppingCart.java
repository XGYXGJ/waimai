package com.waimai.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("shopping_cart")
public class ShoppingCart {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long merchantId;

    private Long dishId;

    private String dishName;

    private String image;

    private BigDecimal price;

    private Integer quantity;

    private LocalDateTime createdAt;
}