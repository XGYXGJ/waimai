package com.waimai.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("notification")
public class Notification {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String type;

    private String title;

    private String content;

    private Integer isRead;

    private LocalDateTime createdAt;
}