package com.waimai.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("bid_log")
public class BidLog {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long campaignId;

    private Long userId;

    private Integer position;

    private Integer clicked;

    private BigDecimal cost;

    private LocalDateTime createdAt;
}