package com.waimai.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("favorite")
public class Favorite {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long merchantId;

    private LocalDateTime createdAt;
}