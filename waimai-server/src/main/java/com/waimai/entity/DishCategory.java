package com.waimai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("dish_category")
public class DishCategory {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    private String name;

    private Integer sort;
}