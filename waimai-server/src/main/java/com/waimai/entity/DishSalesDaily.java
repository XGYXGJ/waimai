package com.waimai.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("dish_sales_daily")
public class DishSalesDaily {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    private Long dishId;

    private LocalDate statDate;

    private Integer quantity;

    private BigDecimal amount;
}