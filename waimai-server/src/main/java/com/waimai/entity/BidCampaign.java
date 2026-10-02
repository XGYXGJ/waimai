package com.waimai.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("bid_campaign")
public class BidCampaign {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    private String keyword;

    private BigDecimal bid;

    private BigDecimal dailyBudget;

    private BigDecimal todaySpent;

    private Integer status;

    private LocalDate startDate;

    private LocalDate endDate;

    private LocalDateTime createdAt;
}