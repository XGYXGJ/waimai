package com.waimai.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("recommend_log")
public class RecommendLog {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long dishId;

    private Long merchantId;

    private String source;

    private BigDecimal score;

    private Integer isExposed;

    private Integer isClicked;

    private Integer isOrdered;

    private String reason;

    private LocalDateTime createdAt;
}