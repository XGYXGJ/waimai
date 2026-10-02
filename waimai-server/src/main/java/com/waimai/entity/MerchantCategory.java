package com.waimai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("merchant_category")
public class MerchantCategory {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String name;

    private String icon;

    private Integer sort;
}