package com.waimai.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("order_status_log")
public class OrderStatusLog {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String fromStatus;

    private String toStatus;

    private String operator;

    private LocalDateTime createdAt;
}