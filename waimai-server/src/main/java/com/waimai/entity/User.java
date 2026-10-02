package com.waimai.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user")
public class User {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String phone;

    private String passwordHash;

    private String nickname;

    private String avatar;

    private String role;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}