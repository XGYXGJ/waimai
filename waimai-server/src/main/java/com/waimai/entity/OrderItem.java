package com.waimai.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("order_item")
public class OrderItem {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long dishId;

    private String dishName;

    private String image;

    private BigDecimal price;

    private Integer quantity;
}