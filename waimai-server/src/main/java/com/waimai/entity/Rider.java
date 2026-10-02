package com.waimai.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("rider")
public class Rider {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String realName;

    private String phone;

    private String vehicle;

    private Integer auditStatus;

    private Integer workStatus;

    private Integer todayOrders;

    private LocalDateTime createdAt;
}