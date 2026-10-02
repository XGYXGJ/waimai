package com.waimai.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("review")
public class Review {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long userId;

    private Long merchantId;

    private Integer rating;

    private String content;

    private String images;

    private String sentiment;

    private BigDecimal sentimentScore;

    private String reply;

    private LocalDateTime createdAt;
}